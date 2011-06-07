/* Copyright (C) 2010 SpringSource
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

package org.grails.datastore.gorm.neo4j.bean.factory

import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.gorm.events.DomainEventListener
import org.springframework.beans.factory.FactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.datastore.mapping.model.MappingContext
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.neo4j.graphdb.GraphDatabaseService

/**
 * Factory bean for constructing a {@link Neo4jDatastore} instance.
 *
 * @author Stefan Armbruster
 */
class Neo4jDatastoreFactoryBean implements FactoryBean<Neo4jDatastore>, ApplicationContextAware {

    GraphDatabaseService graphDatabaseService
    MappingContext mappingContext
    Map<String,String> config = [:]
    ApplicationContext applicationContext

    Neo4jDatastore getObject() {

        def datastore = new Neo4jDatastore(mappingContext, applicationContext, graphDatabaseService)

        applicationContext.addApplicationListener new DomainEventListener(datastore)
        applicationContext.addApplicationListener new AutoTimestampEventListener(datastore)

        datastore.afterPropertiesSet()
        datastore
    }

    Class<?> getObjectType() { Neo4jDatastore.class }

    boolean isSingleton() { true }
}
