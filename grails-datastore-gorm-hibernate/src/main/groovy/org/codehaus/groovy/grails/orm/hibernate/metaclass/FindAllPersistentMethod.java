/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.orm.hibernate.metaclass;

import grails.gorm.DetachedCriteria;
import grails.util.GrailsNameUtils;
import groovy.lang.Closure;
import groovy.lang.MissingMethodException;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.codehaus.groovy.grails.orm.hibernate.exceptions.GrailsQueryException;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Example;
import org.springframework.core.convert.ConversionService;
import org.springframework.orm.hibernate3.HibernateCallback;

/**
 * <p>
 * The "findAll" persistent static method allows searching for instances using
 * either an example instance or an HQL query. Max results and offset parameters could
 * be specified. A GrailsQueryException is thrown if the query is not a valid query
 * for the domain class.
 * <p>
 * Syntax:
 *    DomainClass.findAll(query, params?)
 *    DomainClass.findAll(query, params?, max)
 *    DomainClass.findAll(query, params?, max, offset)
 *    DomainClass.findAll(query, params?, [max:10, offset:5])
 *
 * <p>
 * Examples in Groovy: <code>
 *         // retrieves all accounts
 *         def a = Account.findAll()
 *
 *         // retrieve all accounts ordered by account number
 *         def a = Account.findAll("from Account as a order by a.number asc")
 *
 *         // retrieve first 10 accounts ordered by account number
 *         def a = Account.findAll("from Account as a order by a.number asc",10)
 *
 *         // retrieve first 10 accounts ordered by account number started from 5th account
 *         def a = Account.findAll("from Account as a order by a.number asc",10,5)
 *
 *         // retrieve first 10 accounts ordered by account number started from 5th account
 *         def a = Account.findAll("from Account as a order by a.number asc",[max:10,offset:5])
 *
 *         // with query parameters
 *         def a = Account.find("from Account as a where a.number = ? and a.branch = ?", [38479, "London"])
 *
 *         // with query named parameters
 *         def a = Account.find("from Account as a where a.number = :number and a.branch = :branch", [number:38479, branch:"London"])
 *
 *         // with query named parameters and max results and offset
 *         def a = Account.find("from Account as a where a.number = :number and a.branch = :branch", [number:38479, branch:"London"], 10, 5)
 *
 *         // with query named parameters and max results and offset map
 *         def a = Account.find("from Account as a where a.number = :number and a.branch = :branch", [number:38479, branch:"London"], [max:10, offset:5])
 *
 *         // query by example
 *         def a = new Account()
 *         a.number = 495749357
 *         def a = Account.find(a)
 *
 * </code>
 *
 * @author Graeme Rocher
 * @author Steven Devijver
 * @author Sergey Nebolsin
 *
 * @since 0.1
 */
public class FindAllPersistentMethod extends AbstractStaticPersistentMethod {
    private final ConversionService conversionService;
    
    public FindAllPersistentMethod(SessionFactory sessionFactory, ClassLoader classLoader, GrailsApplication application, ConversionService conversionService) {
        super(sessionFactory, classLoader, Pattern.compile("^findAll$"), application);
        this.conversionService = conversionService;
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Object doInvokeInternal(Class clazz, String methodName, DetachedCriteria additionalCriteria, Object[] arguments) {
        return doInvokeInternal(clazz,methodName, (Closure) null,arguments) ;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected Object doInvokeInternal(final Class clazz, String methodName, Closure additionalCriteria, final Object[] arguments) {
        if (arguments.length == 0) {
            return getHibernateTemplate().loadAll(clazz);
        }

        final Object arg = arguments[0] instanceof CharSequence ? arguments[0].toString() : arguments[0];

        // if the arg is an instance of the class find by example
        if (arg instanceof String) {
            final String query = ((String) arg).trim();
            final String shortName = GrailsNameUtils.getShortName(clazz);
            if (!query.matches("(?i)from(?-i)\\s+[" + clazz.getName() + "|" + shortName + "].*")) {
                throw new GrailsQueryException("Invalid query [" + query + "] for domain class [" + clazz + "]");
            }

            return getHibernateTemplate().executeFind(new HibernateCallback<Object>() {
                public Object doInHibernate(Session session) throws HibernateException, SQLException {
                    Query q = session.createQuery(query);
                    getHibernateTemplate().applySettings(q);

                    Object[] queryArgs = null;
                    Map queryNamedArgs = null;
                    int max = retrieveMaxValue();
                    int offset = retrieveOffsetValue();
                    boolean useCache = useCache();
                    if (arguments.length > 1) {
                        if (arguments[1] instanceof Collection) {
                            queryArgs = GrailsClassUtils.collectionToObjectArray((Collection) arguments[1]);
                        }
                        else if (arguments[1].getClass().isArray()) {
                            queryArgs = (Object[]) arguments[1];
                        }
                        else if (arguments[1] instanceof Map) {
                            queryNamedArgs = (Map) arguments[1];
                        }
                    }

                    if (queryArgs != null) {
                        for (int i = 0; i < queryArgs.length; i++) {
                            if (queryArgs[i] instanceof CharSequence) {
                                q.setParameter(i, queryArgs[i].toString());
                            } else {
                                q.setParameter(i, queryArgs[i]);
                            }
                        }
                    }
                    if (queryNamedArgs != null) {
                        for (Object o : queryNamedArgs.entrySet()) {
                            Map.Entry entry = (Map.Entry) o;
                            if (!(entry.getKey() instanceof String)) {
                                throw new GrailsQueryException("Named parameter's name must be String: " + queryNamedArgs);
                            }
                            String stringKey = (String) entry.getKey();
                            // Won't try to bind these parameters since they are processed separately
                            if (GrailsHibernateUtil.ARGUMENT_MAX.equals(stringKey) || GrailsHibernateUtil.ARGUMENT_OFFSET.equals(stringKey) || GrailsHibernateUtil.ARGUMENT_CACHE.equals(stringKey))
                                continue;
                            Object value = entry.getValue();
                            if (value == null) {
                                q.setParameter(stringKey, null);
                            } else if (value instanceof CharSequence) {
                                q.setParameter(stringKey, value.toString());
                            } else if (List.class.isAssignableFrom(value.getClass())) {
                                q.setParameterList(stringKey, (List) value);
                            } else if (value.getClass().isArray()) {
                                q.setParameterList(stringKey, (Object[]) value);
                            } else {
                                q.setParameter(stringKey, value);
                            }
                        }
                    }
                    if (max > 0) {
                        q.setMaxResults(max);
                    }
                    if (offset > 0) {
                        q.setFirstResult(offset);
                    }
                    q.setCacheable(useCache);
                    return q.list();
                }

                private boolean useCache() {
                    boolean useCache = getHibernateTemplate().isCacheQueries();
                    if (arguments.length > 1 && arguments[arguments.length - 1] instanceof Map) {
                        useCache = retrieveBoolean(arguments[arguments.length - 1], GrailsHibernateUtil.ARGUMENT_CACHE);
                    }
                    return useCache;
                }

                private int retrieveMaxValue() {
                    int result = -1;
                    if (arguments.length > 1) {
                        result = retrieveInt(arguments[1], GrailsHibernateUtil.ARGUMENT_MAX);
                        if (arguments.length > 2 && result == -1) {
                            result = retrieveInt(arguments[2], GrailsHibernateUtil.ARGUMENT_MAX);
                        }
                    }
                    return result;
                }

                private int retrieveOffsetValue() {
                    int result = -1;
                    if (arguments.length > 1) {
                        if (isMapWithValue(arguments[1], GrailsHibernateUtil.ARGUMENT_OFFSET)) {
                            result = ((Number)((Map)arguments[1]).get(GrailsHibernateUtil.ARGUMENT_OFFSET)).intValue();
                        }
                        if (arguments.length > 2 && result == -1) {
                            if (isMapWithValue(arguments[2], GrailsHibernateUtil.ARGUMENT_OFFSET)) {
                                result = retrieveInt(arguments[2], GrailsHibernateUtil.ARGUMENT_OFFSET);
                            }
                            else if (isIntegerOrLong(arguments[1]) && isIntegerOrLong(arguments[2])) {
                                result = ((Number)arguments[2]).intValue();
                            }
                        }
                        if (arguments.length > 3 && result == -1) {
                            if (isIntegerOrLong(arguments[3])) {
                                result = ((Number)arguments[3]).intValue();
                            }
                        }
                    }
                    return result;
                }

                private boolean retrieveBoolean(Object param, String key) {
                    boolean value = false;
                    if (isMapWithValue(param, key)) {
                        value = conversionService.convert(((Map)param).get(key), Boolean.class);
                    }
                    return value;
                }

                private int retrieveInt(Object param, String key) {
                    if (isMapWithValue(param, key)) {
                        return conversionService.convert(((Map) param).get(key),Integer.class);
                    }
                    if (isIntegerOrLong(param)) {
                        return ((Number)param).intValue();
                    }
                    return -1;
                }

                private boolean isIntegerOrLong(Object param) {
                    return (param instanceof Integer) || (param instanceof Long);
                }

                private boolean isMapWithValue(Object param, String key) {
                    return (param instanceof Map) && ((Map)param).containsKey(key);
                }
            });
        }

        if (clazz.isAssignableFrom(arg.getClass())) {
            return getHibernateTemplate().executeFind(new HibernateCallback<Object>() {
                public Object doInHibernate(Session session) throws HibernateException, SQLException {

                    Example example = Example.create(arg).ignoreCase();

                    Criteria crit = session.createCriteria(clazz);
                    getHibernateTemplate().applySettings(crit);
                    crit.add(example);

                    Map argsMap = (arguments.length > 1 && (arguments[1] instanceof Map)) ? (Map) arguments[1] : Collections.EMPTY_MAP;
                    GrailsHibernateUtil.populateArgumentsForCriteria(application,clazz, crit, argsMap, conversionService);
                    return crit.list();
                }
            });
        }

        if (arguments[0] instanceof Map) {
            return getHibernateTemplate().executeFind(new HibernateCallback<Object>() {
                public Object doInHibernate(Session session) throws HibernateException, SQLException {
                    Criteria crit = session.createCriteria(clazz);
                    getHibernateTemplate().applySettings(crit);
                    GrailsHibernateUtil.populateArgumentsForCriteria(application, clazz, crit, (Map)arguments[0], conversionService);
                    return crit.list();
                }
            });
        }

        throw new MissingMethodException(methodName, clazz, arguments);
    }
}
