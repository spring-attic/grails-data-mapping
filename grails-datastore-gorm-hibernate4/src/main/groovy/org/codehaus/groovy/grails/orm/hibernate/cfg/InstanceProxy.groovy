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
package org.codehaus.groovy.grails.orm.hibernate.cfg

import groovy.transform.CompileStatic;

import org.codehaus.groovy.grails.orm.hibernate.HibernateGormInstanceApi
import org.codehaus.groovy.grails.orm.hibernate.HibernateGormValidationApi

@CompileStatic
class InstanceProxy {
    private instance
    private HibernateGormValidationApi validateApi
    private HibernateGormInstanceApi instanceApi

    private final Set<String> validateMethods

    InstanceProxy(instance, HibernateGormInstanceApi instanceApi, HibernateGormValidationApi validateApi) {
        this.instance = instance
        this.instanceApi = instanceApi
        this.validateApi = validateApi
        validateMethods = validateApi.methods*.name as Set<String>
        validateMethods.remove 'getValidator'
        validateMethods.remove 'setValidator'
        validateMethods.remove 'getBeforeValidateHelper'
        validateMethods.remove 'setBeforeValidateHelper'
        validateMethods.remove 'getValidateMethod'
        validateMethods.remove 'setValidateMethod'
    }

    def invokeMethod(String name, args) {
        if (validateMethods.contains(name)) {
            validateApi.invokeMethod(name, [instance, *args])
        }
        else {
            instanceApi.invokeMethod(name, [instance, *args])
        }
    }

    void setProperty(String name, val) {
        instanceApi.setProperty("$name", val)
    }

    def getProperty(String name) {
        instanceApi.getProperty(name)
    }

    void putAt(String name, val) {
        instanceApi.setProperty(name, val)
    }

    def getAt(String name) {
        instanceApi.getProperty(name)
    }
}
