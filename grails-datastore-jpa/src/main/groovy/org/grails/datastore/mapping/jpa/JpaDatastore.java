/* Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.jpa;

import java.util.Map;

import javax.persistence.EntityManagerFactory;

import org.springframework.context.ConfigurableApplicationContext;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.MappingContext;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * Wraps a JPA EntityManagerFactory in the Datastore Abstraction
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JpaDatastore extends AbstractDatastore {

    private EntityManagerFactory entityManagerFactory;
    private JpaTransactionManager transactionManager;

    public JpaDatastore(MappingContext mappingContext,
            EntityManagerFactory entityManagerFactory,
            JpaTransactionManager transactionManager,
            ConfigurableApplicationContext applicationContext) {
        super(mappingContext, null, applicationContext);
        this.entityManagerFactory = entityManagerFactory;
        this.transactionManager = transactionManager;
        initializeConverters(mappingContext);
    }

    @Override
    public JpaSession connect() {
        return (JpaSession) super.connect();
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    public JpaTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    protected Session createSession(Map<String, String> connDetails) {
        return new JpaSession(this, new JpaTemplate(entityManagerFactory), transactionManager);
    }
}
