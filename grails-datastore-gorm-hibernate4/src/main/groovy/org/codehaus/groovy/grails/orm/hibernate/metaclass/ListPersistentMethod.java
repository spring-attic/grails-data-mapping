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
import grails.orm.PagedResultList;
import groovy.lang.Closure;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.orm.hibernate.GrailsHibernateTemplate;
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * The "list" persistent static method. This method lists of of the persistent
 * instances up the maximum specified amount (if any)
 *
 * eg.
 * Account.list(); // list all
 * Account.list(max:10,offset:50,sort:"holder",order:"desc"); // list up to 10, offset by 50, sorted by holder and in descending order
 *
 * @author Graeme Rocher
 */
public class ListPersistentMethod extends AbstractStaticPersistentMethod {

    private static final String METHOD_PATTERN = "^list$";

    public ListPersistentMethod(GrailsApplication grailsApplication, SessionFactory sessionFactory, ClassLoader classLoader) {
        super(sessionFactory, classLoader, Pattern.compile(METHOD_PATTERN), grailsApplication);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Object doInvokeInternal(final Class clazz, String methodName, Closure additionalCriteria, final Object[] arguments) {
        // and list up to the max
        return getHibernateTemplate().executeFind(new GrailsHibernateTemplate.HibernateCallback<Object>() {
            public Object doInHibernate(Session session) throws HibernateException, SQLException {
                Criteria c =  session.createCriteria(clazz);
                getHibernateTemplate().applySettings(c);
                if (arguments.length > 0 && arguments[0] instanceof Map) {
                    Map argMap = (Map)arguments[0];
                    if (argMap.containsKey(GrailsHibernateUtil.ARGUMENT_MAX)) {
                        c.setMaxResults(Integer.MAX_VALUE);
                        GrailsHibernateUtil.populateArgumentsForCriteria(application, clazz, c,argMap);
                        c.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
                        return new PagedResultList(getHibernateTemplate(), c);
                    }

                    GrailsHibernateUtil.populateArgumentsForCriteria(application, clazz, c,argMap);
                    c.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
                    return c.list();
                }

                GrailsHibernateUtil.populateArgumentsForCriteria(application, clazz, c, Collections.EMPTY_MAP);
                c.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
                return c.list();
            }
        });
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Object doInvokeInternal(Class clazz, String methodName, DetachedCriteria additionalCriteria, Object[] arguments) {
        return doInvokeInternal(clazz,methodName, (Closure) null,arguments) ;
    }
}
