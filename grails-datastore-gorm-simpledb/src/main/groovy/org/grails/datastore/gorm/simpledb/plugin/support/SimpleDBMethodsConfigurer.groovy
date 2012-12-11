/* Copyright (C) 2011 SpringSource
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
package org.grails.datastore.gorm.simpledb.plugin.support

import org.grails.datastore.gorm.plugin.support.DynamicMethodsConfigurer

import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.Datastore
import org.springframework.transaction.PlatformTransactionManager
import org.grails.datastore.gorm.simpledb.SimpleDBGormStaticApi
import org.grails.datastore.gorm.simpledb.SimpleDBGormInstanceApi
import org.grails.datastore.gorm.simpledb.SimpleDBGormEnhancer

/**
 *
 * SimpleDB specific dynamic methods configurer
 *
 * @author Roman Stepanenko based on Graeme Rocher
 * @since 0.1
 */
class SimpleDBMethodsConfigurer extends DynamicMethodsConfigurer{

    SimpleDBMethodsConfigurer(Datastore datastore, PlatformTransactionManager transactionManager) {
        super(datastore, transactionManager)
    }

    @Override
    String getDatastoreType() {
        return "SimpleDB"
    }

    @Override
    protected GormStaticApi createGormStaticApi(Class cls, List<FinderMethod> finders) {
        return new SimpleDBGormStaticApi(cls, datastore, finders)
    }

    @Override
    protected GormInstanceApi createGormInstanceApi(Class cls) {
        def api = new SimpleDBGormInstanceApi(cls, datastore)
        api.failOnError = failOnError
        api
    }

    @Override
    protected GormEnhancer createEnhancer() {
        def ge
        if(transactionManager != null)
            ge = new SimpleDBGormEnhancer(datastore, transactionManager)
        else
            ge = new SimpleDBGormEnhancer(datastore)
        ge.failOnError = failOnError
        ge
    }


}
