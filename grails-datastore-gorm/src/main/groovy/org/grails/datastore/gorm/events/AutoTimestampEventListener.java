/* Copyright (C) 2010 SpringSource
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
package org.grails.datastore.gorm.events;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEvent;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener;
import org.grails.datastore.mapping.engine.event.PreInsertEvent;
import org.grails.datastore.mapping.engine.event.PreUpdateEvent;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * An event listener that adds support for GORM-style auto-timestamping
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AutoTimestampEventListener extends AbstractPersistenceEventListener implements MappingContext.Listener {

    public static final String DATE_CREATED_PROPERTY = "dateCreated";
    public static final String LAST_UPDATED_PROPERTY = "lastUpdated";

    private Map<PersistentEntity, Boolean> entitiesWithDateCreated = new ConcurrentHashMap<PersistentEntity, Boolean>();
    private Map<PersistentEntity, Boolean> entitiesWithLastUpdated = new ConcurrentHashMap<PersistentEntity, Boolean>();

    public AutoTimestampEventListener(final Datastore datastore) {
        super(datastore);

        for (PersistentEntity persistentEntity : datastore.getMappingContext().getPersistentEntities()) {
            storeDateCreatedInfo(persistentEntity);
            storeLastUpdatedInfo(persistentEntity);
        }

        datastore.getMappingContext().addMappingContextListener(this);
    }

    @Override
    protected void onPersistenceEvent(final AbstractPersistenceEvent event) {
        if (event instanceof PreInsertEvent) {
            beforeInsert(event.getEntity(), event.getEntityAccess());
        }
        else if (event instanceof PreUpdateEvent) {
            beforeUpdate(event.getEntity(), event.getEntityAccess());
        }
    }

    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return PreInsertEvent.class.isAssignableFrom(eventType) ||
               PreUpdateEvent.class.isAssignableFrom(eventType);
    }

    public boolean beforeInsert(PersistentEntity entity, EntityAccess ea) {
        if (hasDateCreated(entity)) {
            final Date now = new Date();
            ea.setProperty(DATE_CREATED_PROPERTY, now);

            if (hasLastupdated(entity)) {
                ea.setProperty(LAST_UPDATED_PROPERTY, now);
            }
        }
        return true;
    }

    public boolean beforeUpdate(PersistentEntity entity, EntityAccess ea) {
        if (hasLastupdated(entity)) {
            ea.setProperty(LAST_UPDATED_PROPERTY, new Date());
        }
        return true;
    }

    private boolean hasLastupdated(PersistentEntity entity) {
        return entitiesWithLastUpdated.containsKey(entity) && entitiesWithLastUpdated.get(entity);
    }

    private boolean hasDateCreated(PersistentEntity entity) {
        return entitiesWithDateCreated.containsKey(entity)&& entitiesWithDateCreated.get(entity);
    }

    private void storeLastUpdatedInfo(PersistentEntity persistentEntity) {
        entitiesWithLastUpdated.put(persistentEntity, persistentEntity.hasProperty(LAST_UPDATED_PROPERTY, Date.class));
    }

    private void storeDateCreatedInfo(PersistentEntity persistentEntity) {
        entitiesWithDateCreated.put(persistentEntity, persistentEntity.hasProperty(DATE_CREATED_PROPERTY, Date.class));
    }

    public void persistentEntityAdded(PersistentEntity entity) {
        storeDateCreatedInfo(entity);
        storeLastUpdatedInfo(entity);
    }
}
