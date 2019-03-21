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
package org.grails.datastore.mapping.simpledb;

import static org.grails.datastore.mapping.config.utils.ConfigUtils.read;

import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.simpledb.config.SimpleDBMappingContext;
import org.grails.datastore.mapping.simpledb.model.types.SimpleDBTypeConverterRegistrar;
import org.grails.datastore.mapping.simpledb.util.DelayAfterWriteSimpleDBTemplateDecorator;
import org.grails.datastore.mapping.simpledb.util.SimpleDBTemplate;
import org.grails.datastore.mapping.simpledb.util.SimpleDBTemplateImpl;

/**
 * A Datastore implementation for the AWS SimpleDB document store.
 *
 * @author Roman Stepanenko based on Graeme Rocher code for MongoDb and Redis
 * @since 0.1
 */
public class SimpleDBDatastore extends AbstractDatastore implements InitializingBean, MappingContext.Listener {

    public static final String SECRET_KEY = "secretKey";
    public static final String ACCESS_KEY = "accessKey";
    public static final String DOMAIN_PREFIX_KEY = "domainNamePrefix";
    public static final String DELAY_AFTER_WRITES = "delayAfterWrites"; //used for testing - to fight eventual consistency if this flag value is 'true' it will add about 10 sec pause after writes

//    private Map<PersistentEntity, SimpleDBTemplate> simpleDBTemplates = new ConcurrentHashMap<PersistentEntity, SimpleDBTemplate>();
    private SimpleDBTemplate simpleDBTemplate;  //currently there is no need to create template per entity, we can share same instance
    private String domainNamePrefix;

    public SimpleDBDatastore() {
        this(new SimpleDBMappingContext(), Collections.<String, String>emptyMap(), null);
    }

    /**
     * Constructs a SimpleDBDatastore using the given MappingContext and connection details map.
     *
     * @param mappingContext The MongoMappingContext
     * @param connectionDetails The connection details containing the {@link #ACCESS_KEY} and {@link #SECRET_KEY} settings
     */
    public SimpleDBDatastore(MappingContext mappingContext,
            Map<String, String> connectionDetails, ConfigurableApplicationContext ctx) {
        super(mappingContext, connectionDetails, ctx);

        if (mappingContext != null) {
            mappingContext.addMappingContextListener(this);
        }

        initializeConverters(mappingContext);

        domainNamePrefix = read(String.class, DOMAIN_PREFIX_KEY, connectionDetails, null);
    }

    public SimpleDBDatastore(MappingContext mappingContext, Map<String, String> connectionDetails) {
        this(mappingContext, connectionDetails, null);
    }

    public SimpleDBDatastore(MappingContext mappingContext) {
        this(mappingContext, Collections.<String, String>emptyMap(), null);
    }

    public SimpleDBTemplate getSimpleDBTemplate(PersistentEntity entity) {
//        return simpleDBTemplates.get(entity);
        return simpleDBTemplate;
    }

    public SimpleDBTemplate getSimpleDBTemplate() {
        return simpleDBTemplate;
    }

    @Override
    protected Session createSession(Map<String, String> connDetails) {
        return new SimpleDBSession(this, getMappingContext(), getApplicationEventPublisher());
    }

    public void afterPropertiesSet() throws Exception {
        for (PersistentEntity entity : mappingContext.getPersistentEntities()) {
            // Only create SimpleDB templates for entities that are mapped with SimpleDB
            if (!entity.isExternal()) {
                createSimpleDBTemplate(entity);
            }
        }
    }

    protected void createSimpleDBTemplate(PersistentEntity entity) {
        if (simpleDBTemplate == null) {
            String accessKey = read(String.class, ACCESS_KEY, connectionDetails, null);
            String secretKey = read(String.class, SECRET_KEY, connectionDetails, null);
            String delayAfterWrite = read(String.class, DELAY_AFTER_WRITES, connectionDetails, null);

            simpleDBTemplate = new SimpleDBTemplateImpl(accessKey, secretKey);
            if (Boolean.parseBoolean(delayAfterWrite)) {
                simpleDBTemplate = new DelayAfterWriteSimpleDBTemplateDecorator(simpleDBTemplate, 10*1000);
            }
        }
    }

    /**
     * If specified, returns domain name prefix so that same AWS account can be used for more than one environment (DEV/TEST/PROD etc).
     * @return null if name was not specified in the configuration
     */
    public String getDomainNamePrefix() {
        return domainNamePrefix;
    }

    public void persistentEntityAdded(PersistentEntity entity) {
        createSimpleDBTemplate(entity);
    }

    @Override
    protected void initializeConverters(@SuppressWarnings("hiding") MappingContext mappingContext) {
        final ConverterRegistry conversionService = mappingContext.getConverterRegistry();
        new SimpleDBTypeConverterRegistrar().register(conversionService);
    }
}
