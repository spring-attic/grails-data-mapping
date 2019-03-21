/*
 * Copyright (c) 2010 by J. Brisbin <jon@jbrisbin.com>
 *     Portions (c) 2010 by NPC International, Inc. or the
 *     original author(s).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.mapping.riak.engine;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.keyvalue.riak.core.QosParameters;
import org.springframework.data.keyvalue.riak.core.RiakTemplate;
import org.springframework.data.keyvalue.riak.core.RiakValue;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationIndexer;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.PropertyValueIndexer;
import org.grails.datastore.mapping.keyvalue.engine.AbstractKeyValueEntityPersister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.proxy.EntityProxy;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.riak.RiakEntry;
import org.grails.datastore.mapping.riak.RiakSession;
import org.grails.datastore.mapping.riak.collection.RiakEntityIndex;
import org.grails.datastore.mapping.riak.query.RiakQuery;

/**
 * An {@link org.grails.datastore.mapping.engine.EntityPersister} implementation for
 * the Riak Key/Value store.
 *
 * @author J. Brisbin <jon@jbrisbin.com>
 */
@SuppressWarnings({"unchecked"})
public class RiakEntityPersister extends AbstractKeyValueEntityPersister<Map, Long> {

    private final static String DISCRIMINATOR = "__entity__";
    private final static String DESCENDANTS = "descendants";
    private final Logger log = LoggerFactory.getLogger(getClass());
    private RiakTemplate riakTemplate;

    public RiakEntityPersister(MappingContext context, PersistentEntity entity, Session session,
               final RiakTemplate riakTemplate, ApplicationEventPublisher publisher) {
        super(context, entity, session, publisher);
        this.riakTemplate = riakTemplate;
    }

    @Override
    protected void deleteEntry(String family, Long key, Object entry) {
        riakTemplate.deleteKeys(String.format("%s:%s", family, key));
    }

    @Override
    protected Long generateIdentifier(PersistentEntity persistentEntity, Map id) {
        return UUID.randomUUID().getLeastSignificantBits();
    }

    public RiakEntityIndex getAllEntityIndex() {
        return new RiakEntityIndex(riakTemplate, getEntityFamily());
    }

    @Override
    public PropertyValueIndexer getPropertyIndexer(PersistentProperty property) {
        return new RiakPropertyValueIndexer(riakTemplate, getMappingContext(), this, property);
    }

    @Override
    public AssociationIndexer getAssociationIndexer(Map nativeEntry, Association association) {
        return new RiakAssociationIndexer(riakTemplate,
                getMappingContext().getConversionService(),
                association);
    }

    @Override
    protected Map createNewEntry(String family) {
        return new RiakEntry(family);
    }

    @Override
    protected Object getEntryValue(Map nativeEntry, String property) {
        PersistentProperty prop = getPersistentEntity().getPropertyByName(property);
        if (null != prop) {
            if (prop.getType() == Date.class) {
                // If this property is a Date object, we need to handle it as a Long,
                // otherwise it will get improperly formatted in the Query implementation.
                return new Date(Long.parseLong(nativeEntry.get(property).toString()));
            }
            if (prop.getType() == Calendar.class) {
                // If this property is a Calendar object, we need to handle it as a Long,
                // otherwise it will get improperly formatted in the Query implementation.
                Calendar c = Calendar.getInstance();
                c.setTime(new Date(Long.parseLong(nativeEntry.get(property).toString())));
                return c;
            }
            if (prop.getType() == Boolean.class) {
                return (nativeEntry.containsKey(property) ?
                        Boolean.valueOf(nativeEntry.get(property).toString()) :
                        false);
            }
        }
        return nativeEntry.get(property);
    }

    @Override
    protected void setEntryValue(Map nativeEntry, String key, Object value) {
        if (null == value) {
            return;
        }

        if (value instanceof Date) {
            // Date handling again, like above
            nativeEntry.put(key, ((Date) value).getTime());
        } else if (value instanceof Calendar) {
            // Calendar handling again, like above
            nativeEntry.put(key, ((Calendar) value).getTime().getTime());
        } else if (value instanceof Boolean) {
            nativeEntry.put(key, value);
        } else if (shouldConvert(value)) {
            final ConversionService conversionService = getMappingContext().getConversionService();
            nativeEntry.put(key, conversionService.convert(value, String.class));
        }
    }

    @Override
    protected PersistentEntity discriminatePersistentEntity(PersistentEntity persistentEntity, Map nativeEntry) {
        if (nativeEntry.containsKey(DISCRIMINATOR)) {
            PersistentEntity e = getMappingContext().getChildEntityByDiscriminator(persistentEntity.getRootEntity(),
                    nativeEntry.get(DISCRIMINATOR).toString());
            if (null != e) {
                return e;
            }
            return persistentEntity;
        }
        return persistentEntity;
    }

    @Override
    protected Map retrieveEntry(PersistentEntity persistentEntity, String family, Serializable key) {
        // Not sure this check is actually necessary, but I was seeing null keys being passed
        // during tests, so handle it explicitly.
        if (null == key) {
            return null;
        }

        Set<String> descendants = null;
        RiakValue<Map> v = null;

        // Check for any descendants this entity might have.
        String bucket = persistentEntity.getName() + ".metadata";
        if (riakTemplate.containsKey(bucket, DESCENDANTS)) {
            descendants = riakTemplate.getAsType(bucket, DESCENDANTS, Set.class);
            v = riakTemplate.getWithMetaData(family,
                    (key instanceof Long ? (Long) key : Long.parseLong(key.toString())),
                    Map.class);
            if (log.isDebugEnabled()) {
                log.debug(String.format("retrieveEntry(): entity=%s, family=%s, key=%s, values=%s",
                        persistentEntity.getName(), family, key, v));
            }
        }

        if (null == v && null != descendants) {
            // Search through all descendants, as well.
            for (String d : descendants) {
                v = riakTemplate.getWithMetaData(d,
                        (key instanceof Long ? (Long) key : Long.parseLong(key.toString())),
                        Map.class);
                if (null != v) {
                    break;
                }
            }
        }

        return (null != v ? v.get() : null);
    }

    @Override
    protected Long storeEntry(final PersistentEntity persistentEntity, final EntityAccess entityAccess,
                              final Long storeId, final Map nativeEntry) {
        Map<String, String> metaData = null;

        // Quality Of Service parameters (r, w, dw)
        QosParameters qosParams = ((RiakSession) getSession()).getQosParameters();

        if (!persistentEntity.isRoot()) {
            // This is a subclass, find our ancestry.
            List<String> ancestors = getAncestors(persistentEntity);
            if (log.isDebugEnabled()) {
                log.debug("Storing entity ancestor metadata for " + ancestors);
            }

            for (String s : ancestors) {
                String bucket = s + ".metadata";
                if (riakTemplate.containsKey(bucket, DESCENDANTS)) {
                    // Build up a list of our ancestors and store it with the special metadata on this entity.
                    Set<String> descendants = riakTemplate.getAsType(bucket, DESCENDANTS, Set.class);
                    if (null == descendants) {
                        descendants = new LinkedHashSet<String>();
                    }
                    descendants.add(persistentEntity.getName());

                    riakTemplate.set(bucket, DESCENDANTS, descendants);
                }
            }

            metaData = new LinkedHashMap<String, String>();

            // Also save this information into a metadata entry for use down the road.
            metaData.put("X-Riak-Meta-Entity", persistentEntity.getName());
            // Save discriminator information
            nativeEntry.put(DISCRIMINATOR, persistentEntity.getDiscriminator());
        }

        riakTemplate.setWithMetaData(persistentEntity.getName(),
                storeId, nativeEntry, metaData, qosParams);

        return storeId;
    }

    @Override
    protected void updateEntry(final PersistentEntity persistentEntity, final EntityAccess entityAccess,
                               final Long key, final Map entry) {
        storeEntry(persistentEntity, entityAccess, key, entry);
    }

    @Override
    protected void deleteEntries(String family, List<Long> keys) {
        for (Long key : keys) {
            riakTemplate.delete(family, key);
        }
    }

    protected boolean shouldConvert(Object value) {
        return !getMappingContext().isPersistentEntity(value) && !(value instanceof EntityProxy);
    }

    public Query createQuery() {
        return new RiakQuery(session, getPersistentEntity(), riakTemplate);
    }

    protected String getRootFamily(PersistentEntity entity) {
        String family = getFamily(entity, entity.getMapping());
        if (!entity.isRoot()) {
            PersistentEntity root = entity.getRootEntity();
            family = getFamily(root, root.getMapping());
        }
        return family;
    }

    protected List<String> getAncestors(PersistentEntity entity) {
        List<String> ancestors = new LinkedList<String>();
        PersistentEntity parent = entity.getParentEntity();
        ancestors.add(parent.getName());
        if (!parent.isRoot()) {
            ancestors.addAll(getAncestors(parent));
        }
        return ancestors;
    }
}
