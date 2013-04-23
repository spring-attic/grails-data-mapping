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
package org.codehaus.groovy.grails.orm.hibernate;

import grails.util.CollectionUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.metamodel.source.MetadataImplementor;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

public class EventListenerIntegrator implements Integrator {

    protected HibernateEventListeners hibernateEventListeners;
    protected Map<String, Object> eventListeners;

    public EventListenerIntegrator(HibernateEventListeners hibernateEventListeners, Map<String, Object> eventListeners) {
        this.hibernateEventListeners = hibernateEventListeners;
        this.eventListeners = eventListeners;
    }

    @SuppressWarnings("unchecked")
    protected static final List<EventType<? extends Serializable>> TYPES = CollectionUtils.newList(
            EventType.AUTO_FLUSH,
            EventType.MERGE,
            EventType.PERSIST,
            EventType.PERSIST_ONFLUSH,
            EventType.DELETE,
            EventType.DIRTY_CHECK,
            EventType.EVICT,
            EventType.FLUSH,
            EventType.FLUSH_ENTITY,
            EventType.LOAD,
            EventType.INIT_COLLECTION,
            EventType.LOCK,
            EventType.REFRESH,
            EventType.REPLICATE,
            EventType.SAVE_UPDATE,
            EventType.SAVE,
            EventType.UPDATE,
            EventType.PRE_LOAD,
            EventType.PRE_UPDATE,
            EventType.PRE_DELETE,
            EventType.PRE_INSERT,
            EventType.PRE_COLLECTION_RECREATE,
            EventType.PRE_COLLECTION_REMOVE,
            EventType.PRE_COLLECTION_UPDATE,
            EventType.POST_LOAD,
            EventType.POST_UPDATE,
            EventType.POST_DELETE,
            EventType.POST_INSERT,
            EventType.POST_COMMIT_UPDATE,
            EventType.POST_COMMIT_DELETE,
            EventType.POST_COMMIT_INSERT,
            EventType.POST_COLLECTION_RECREATE,
            EventType.POST_COLLECTION_REMOVE,
            EventType.POST_COLLECTION_UPDATE);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void integrate(Configuration config, SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {

        EventListenerRegistry listenerRegistry = serviceRegistry.getService(EventListenerRegistry.class);

        if (eventListeners != null) {
            for (Map.Entry<String, Object> entry : eventListeners.entrySet()) {
                EventType type = EventType.resolveEventTypeByName(entry.getKey());
                Object listenerObject = entry.getValue();
                if (listenerObject instanceof Collection) {
                    appendListeners(listenerRegistry, type, (Collection)listenerObject);
                }
                else if (listenerObject != null) {
                    appendListeners(listenerRegistry, type, Collections.singleton(listenerObject));
                }
            }
        }

        if (hibernateEventListeners != null && hibernateEventListeners.getListenerMap() != null) {
            Map<String,Object> listenerMap = hibernateEventListeners.getListenerMap();
            for (EventType<?> type : TYPES) {
                appendListeners(listenerRegistry, type, listenerMap);
            }
        }

        // register workaround for GRAILS-8988 (do nullability checks for inserts in last PreInsertEventListener)
        ClosureEventTriggeringInterceptor.addNullabilityCheckerPreInsertEventListener(listenerRegistry);
    }

    protected <T> void appendListeners(EventListenerRegistry listenerRegistry,
            EventType<T> eventType, Collection<T> listeners) {

        EventListenerGroup<T> group = listenerRegistry.getEventListenerGroup(eventType);
        for (T listener : listeners) {
            if (listener != null) {
                group.appendListener(listener);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> void appendListeners(final EventListenerRegistry listenerRegistry,
            final EventType<T> eventType, final Map<String, Object> listeners) {

        Object listener = listeners.get(eventType.eventName());
        if (listener != null) {
            listenerRegistry.appendListeners(eventType, (T)listener);
        }
    }

    public void integrate(MetadataImplementor metadata, SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        // nothing to do
    }

    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        // nothing to do
    }
}
