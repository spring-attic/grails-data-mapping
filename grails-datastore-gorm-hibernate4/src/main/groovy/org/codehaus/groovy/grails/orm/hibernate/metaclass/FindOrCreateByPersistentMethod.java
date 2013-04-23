/* 
 * Copyright 2013 the original author or authors.
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
import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.orm.hibernate.HibernateDatastore;
import org.hibernate.SessionFactory;

@SuppressWarnings("rawtypes")
public class FindOrCreateByPersistentMethod extends AbstractFindByPersistentMethod {

    private static final String METHOD_PATTERN = "(findOrCreateBy)([A-Z]\\w*)";

    public FindOrCreateByPersistentMethod(HibernateDatastore datastore, GrailsApplication application,SessionFactory sessionFactory, ClassLoader classLoader) {
        this(datastore, application,sessionFactory, classLoader, METHOD_PATTERN);
    }

    public FindOrCreateByPersistentMethod(HibernateDatastore datastore, GrailsApplication application,SessionFactory sessionFactory, ClassLoader classLoader, String pattern) {
        super(datastore, application,sessionFactory, classLoader, Pattern.compile(pattern), OPERATORS);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object doInvokeInternalWithExpressions(Class clazz,
                                                     String methodName, Object[] arguments, List expressions,
                                                     String operatorInUse, DetachedCriteria detachedCriteria, Closure additionalCriteria) {
        boolean isValidMethod = true;

        if (OPERATOR_OR.equals(operatorInUse)) {
            isValidMethod = false;
        }

        Iterator iterator = expressions.iterator();
        while (isValidMethod && iterator.hasNext()) {
            GrailsMethodExpression gme = (GrailsMethodExpression) iterator.next();
            isValidMethod = GrailsMethodExpression.EQUAL.equals(gme.type);
        }

        if (!isValidMethod) {
            throw new MissingMethodException(methodName, clazz, arguments);
        }
        Object result = super.doInvokeInternalWithExpressions(clazz, methodName, arguments,
                expressions, operatorInUse, detachedCriteria, additionalCriteria);

        if (result == null) {
            Map m = new HashMap();
            for (Object o : expressions) {
                GrailsMethodExpression gme = (GrailsMethodExpression) o;
                m.put(gme.getPropertyName(), gme.getArguments()[0]);
            }
            MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(clazz);
            result = metaClass.invokeConstructor(new Object[]{m});
            if (shouldSaveOnCreate()) {
                metaClass.invokeMethod(result, "save", null);
            }
        }

        return result;
    }

    protected boolean shouldSaveOnCreate() {
        return false;
    }
}
