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
package org.grails.datastore.mapping.mongo.engine;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.*;

import com.mongodb.*;
import org.bson.types.ObjectId;
import org.grails.datastore.mapping.core.OptimisticLockingException;
import org.grails.datastore.mapping.core.SessionImplementor;
import org.grails.datastore.mapping.core.impl.PendingUpdateAdapter;
import org.grails.datastore.mapping.engine.AssociationIndexer;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.NativeEntryEntityPersister;
import org.grails.datastore.mapping.engine.PropertyValueIndexer;
import org.grails.datastore.mapping.model.*;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.mongo.MongoDatastore;
import org.grails.datastore.mapping.mongo.MongoSession;
import org.grails.datastore.mapping.mongo.config.MongoAttribute;
import org.grails.datastore.mapping.mongo.config.MongoMappingContext;
import org.grails.datastore.mapping.mongo.query.MongoQuery;
import org.grails.datastore.mapping.query.Query;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.DataAccessException;

import org.springframework.data.mongodb.core.DbCallback;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * A {@link org.grails.datastore.mapping.engine.EntityPersister} implementation for the Mongo document store
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("ALL")
public class MongoEntityPersister extends NativeEntryEntityPersister<DBObject, Object> {

    private static final String NEXT_ID_SUFFIX = ".next_id";
    private MongoTemplate mongoTemplate;
    private String collectionName;
    private boolean hasNumericalIdentifier = false;
    private boolean hasStringIdentifier = false;

    public static final String MONGO_ID_FIELD = "_id";
    public static final String MONGO_CLASS_FIELD = "_class";

    public MongoEntityPersister(MappingContext mappingContext, PersistentEntity entity,
             MongoSession mongoSession, ApplicationEventPublisher publisher) {
        super(mappingContext, entity, mongoSession, publisher);
        MongoDatastore datastore = (MongoDatastore) mongoSession.getDatastore();
        mongoTemplate = datastore.getMongoTemplate(entity);
        collectionName = datastore.getCollectionName(entity);

        if(!(entity instanceof EmbeddedPersistentEntity)) {

            PersistentProperty identity = entity.getIdentity();
            if(identity != null) {
                hasNumericalIdentifier = Long.class.isAssignableFrom(identity.getType());
                hasStringIdentifier = String.class.isAssignableFrom(identity.getType());
            }
        }
    }


    @Override
    protected void refreshObjectStateFromNativeEntry(PersistentEntity persistentEntity, Object obj, Serializable nativeKey, DBObject nativeEntry, boolean isEmbedded) {
        if(isEmbedded) {
            Object id = nativeEntry.get(MONGO_ID_FIELD);
            super.refreshObjectStateFromNativeEntry(persistentEntity, obj, (Serializable) id, nativeEntry, isEmbedded);
        }
        else {

            super.refreshObjectStateFromNativeEntry(persistentEntity, obj, nativeKey, nativeEntry, isEmbedded);
        }
    }

    @Override
    protected DBObject getEmbedded(DBObject nativeEntry, String key) {
        final Object embeddedDocument = nativeEntry.get(key);
        if (embeddedDocument instanceof DBObject) {
            return (DBObject) embeddedDocument;
        }
        return null;
    }

    @Override
    protected void setEmbedded(DBObject nativeEntry, String key, DBObject embeddedEntry) {
        nativeEntry.put(key, embeddedEntry);
    }

    @Override
    protected void setEmbeddedCollection(final DBObject nativeEntry, final String key, Collection<?> instances,
            List<DBObject> embeddedEntries) {
        if (instances == null || instances.isEmpty()) {
            nativeEntry.put(key, null);
            return;
        }

        nativeEntry.put(key, embeddedEntries.toArray());
    }

    /**
     * Implementors who want to support one-to-many associations embedded should implement this method
     *
     * @param association The association
     * @param ea
     * @param nativeEntry
     * @return A list of keys loaded from the embedded instance
     */
    @Override
    protected List loadEmbeddedCollectionKeys(Association association, EntityAccess ea, DBObject nativeEntry) {
        if(nativeEntry != null) {

            Object entry = nativeEntry.get(getPropertyKey(association));
            List keys = new ArrayList();
            if(entry instanceof List) {
                List entries = (List) entry;
                for (Object o : entries) {
                    if(o instanceof DBRef) {
                        DBRef dbref = (DBRef) o;
                        keys.add(dbref.getId());
                    }
                    else if(o != null) {
                        keys.add(o);
                    }
                    else {
                        keys.add(null);
                    }
                }
            }
            return keys;
        }
        return super.loadEmbeddedCollectionKeys(association, ea, nativeEntry);
    }

    @Override
    protected void setEmbeddedCollectionKeys(Association association, EntityAccess embeddedEntityAccess, DBObject embeddedEntry, List<Serializable> keys) {
        List<DBRef> dbRefs = new ArrayList<DBRef>();
        for (Object foreignKey : keys) {
            dbRefs.add(new DBRef((DB) session.getNativeInterface(), getCollectionName(association.getAssociatedEntity()), foreignKey));
        }
        embeddedEntry.put(association.getName(), dbRefs);
    }

    @Override
    protected void loadEmbeddedCollection(@SuppressWarnings("rawtypes") EmbeddedCollection embeddedCollection,
            EntityAccess ea, Object embeddedInstances, String propertyKey) {

        Collection<Object> instances;
        if (List.class.isAssignableFrom(embeddedCollection.getType())) {
            instances = new ArrayList<Object>();
        }
        else {
            instances = new HashSet<Object>();
        }

        if (embeddedInstances instanceof BasicDBList) {
            BasicDBList list = (BasicDBList)embeddedInstances;
            for (Object dbo : list) {
                if (dbo instanceof BasicDBObject) {
                    BasicDBObject nativeEntry = (BasicDBObject)dbo;
                    Object instance =
                            createObjectFromEmbeddedNativeEntry(embeddedCollection.getAssociatedEntity(), nativeEntry);
                    instances.add(instance);
                }
            }
        }

        ea.setProperty(embeddedCollection.getName(), instances);
    }

    @Override
    protected boolean isEmbeddedEntry(Object entry) {
        return entry instanceof DBObject;
    }

    public Query createQuery() {
        return new MongoQuery((MongoSession) getSession(), getPersistentEntity());
    }

    @Override
    protected boolean doesRequirePropertyIndexing() {
        return false;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected List<Object> retrieveAllEntities(PersistentEntity persistentEntity,
            Iterable<Serializable> keys) {

        Query query = session.createQuery(persistentEntity.getJavaClass());

        if (keys instanceof List) {
            List actualKeys = new ArrayList();
            Iterator iterator = keys.iterator();
            while (iterator.hasNext()) {
                Object key = iterator.next();
                Object id = getIdentifierForKey(key);
                actualKeys.add(id);

            }
            query.in(persistentEntity.getIdentity().getName(), actualKeys);
        }
        else {
            List<Serializable> keyList = new ArrayList<Serializable>();
            for (Serializable key : keys) {
                keyList.add(key);
            }
            query.in(persistentEntity.getIdentity().getName(), keyList);
        }

        List<Object> entityResults = new ArrayList<Object>();
        Iterator<Serializable> keyIterator = keys.iterator();
        Map<Serializable, Object> resultMap = new HashMap<Serializable, Object>();
        for (Object o : query.list()) {
            if(o instanceof DBObject) {
                DBObject dbo = (DBObject) o;
                o = createObjectFromNativeEntry(getPersistentEntity(), (Serializable) dbo.get(MONGO_ID_FIELD), dbo);
            }
            resultMap.put(getObjectIdentifier(o), o);
        }
        while (keyIterator.hasNext()) {
            Object key = getIdentifierForKey(keyIterator.next());
            Object o = resultMap.get(key);
            if(o != null)
                entityResults.add(o);

        }

        return entityResults;
    }

    private Object getIdentifierForKey(Object key) {
        Object id = key;
        if(key instanceof DBRef) {
            DBRef ref = (DBRef) key;
            id = ref.getId();
        }
        return id;
    }

    @Override
    protected List<Object> retrieveAllEntities(PersistentEntity persistentEntity, Serializable[] keys) {
        return retrieveAllEntities(persistentEntity, Arrays.asList(keys));
    }

    @Override
    public String getEntityFamily() {
        return collectionName;
    }

    @Override
    protected void deleteEntry(String family, final Object key, final Object entry) {
        mongoTemplate.execute(new DbCallback<Object>() {
            public Object doInDB(DB con) throws MongoException, DataAccessException {
                DBCollection dbCollection = getCollection(con);

                DBObject dbo = createDBObjectWithKey(key);
                MongoSession mongoSession = (MongoSession) session;
                WriteConcern writeConcern = mongoSession.getDeclaredWriteConcern(getPersistentEntity());
                if(writeConcern != null)
                    dbCollection.remove(dbo, writeConcern);
                else
                    dbCollection.remove(dbo);
                return null;
            }

            protected DBCollection getCollection(DB con) {
                return con.getCollection(getCollectionName(getPersistentEntity()));
            }
        });
    }

    @Override
    protected Object generateIdentifier(final PersistentEntity persistentEntity,
            final DBObject nativeEntry) {
        return mongoTemplate.execute(new DbCallback<Object>() {
            public Object doInDB(DB con) throws MongoException, DataAccessException {

                @SuppressWarnings("hiding") String collectionName = getCollectionName(persistentEntity, nativeEntry);

                DBCollection dbCollection = con.getCollection(collectionName + NEXT_ID_SUFFIX);

                // If there is a numeric identifier then we need to rely on optimistic concurrency controls to obtain a unique identifer
                // sequence. If the identifier is not numeric then we assume BSON ObjectIds.
                if (hasNumericalIdentifier) {
                    while (true) {
                        DBCursor result = dbCollection.find().sort(new BasicDBObject(MONGO_ID_FIELD, -1)).limit(1);

                        long nextId;
                        if (result.hasNext()) {
                            final Long current = getMappingContext().getConversionService().convert(
                                   result.next().get(MONGO_ID_FIELD), Long.class);
                            nextId = current + 1;
                        }
                        else {
                            nextId = 1;
                        }

                        nativeEntry.put(MONGO_ID_FIELD, nextId);
                        final WriteResult writeResult = dbCollection.insert(nativeEntry);
                        final CommandResult lastError = writeResult.getLastError();
                        if (lastError.ok()) {
                            break;
                        }

                        final Object code = lastError.get("code");
                        // duplicate key error try again
                        if (code != null && code.equals(11000)) {
                            continue;
                        }
                        break;
                    }

                    return nativeEntry.get(MONGO_ID_FIELD);
                }

                ObjectId objectId = ObjectId.get();
                if (ObjectId.class.isAssignableFrom(persistentEntity.getIdentity().getType())) {
                    nativeEntry.put(MONGO_ID_FIELD, objectId);
                    return objectId;
                }

                String stringId = objectId.toString();
                nativeEntry.put(MONGO_ID_FIELD, stringId);
                return stringId;
            }
        });
    }

    @SuppressWarnings("rawtypes")
    @Override
    public PropertyValueIndexer getPropertyIndexer(PersistentProperty property) {
        // We don't need to implement this for Mongo since Mongo automatically creates indexes for us
        return null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public AssociationIndexer getAssociationIndexer(DBObject nativeEntry, Association association) {
        return new MongoAssociationIndexer(nativeEntry, association, (MongoSession) session);
    }

    @Override
    protected DBObject createNewEntry(String family) {
        BasicDBObject dbo = new BasicDBObject();
        PersistentEntity persistentEntity = getPersistentEntity();
        if (!persistentEntity.isRoot()) {
            dbo.put(MONGO_CLASS_FIELD, persistentEntity.getDiscriminator());
        }

        return dbo;
    }

    @Override
    protected Object getEntryValue(DBObject nativeEntry, String property) {
        Object value = nativeEntry.get(property);
        if(value instanceof DBRef) {
            return getIdentifierForKey(value);
        }
        return value;
    }

    @Override
    protected Object formulateDatabaseReference(PersistentEntity persistentEntity,
              @SuppressWarnings("rawtypes") Association association, Serializable associationId) {
        DB db = (DB) session.getNativeInterface();
        boolean isReference = isReference(association);
        if(isReference)
            return new DBRef(db, getCollectionName(association.getAssociatedEntity()), associationId);
        else
            return associationId;
    }

    private boolean isReference(Association association) {
        PropertyMapping mapping = association.getMapping();
        if(mapping != null) {
            MongoAttribute attribute = (MongoAttribute) mapping.getMappedForm();
            if(attribute != null)
                return attribute.isReference();
        }
        return true;
    }

    @Override
    protected void setEntryValue(DBObject nativeEntry, String key, Object value) {
        MappingContext mappingContext = getMappingContext();
        setDBObjectValue(nativeEntry, key, value, mappingContext);
    }

    @Override
    protected String getPropertyKey(PersistentProperty prop) {
        if(prop instanceof Identity) {
            return MONGO_ID_FIELD;
        }
        return super.getPropertyKey(prop);
    }

    public static void setDBObjectValue(DBObject nativeEntry, String key, Object value, MappingContext mappingContext) {
        Object nativeValue = getSimpleNativePropertyValue(value, mappingContext);
        nativeEntry.put(key, nativeValue);
    }

    /**
     * Convert a value into a type suitable for use in Mongo. Collections and maps are converted recursively. The
     * mapping context is used for the conversion if possible, otherwise toString() is the eventual fallback.
     * @param value The value to convert (or null)
     * @param mappingContext The mapping context.
     * @return The converted value (or null)
     */
    public static Object getSimpleNativePropertyValue(Object value, MappingContext mappingContext) {
        Object nativeValue;

        if(value == null || mappingContext.isPersistentEntity(value)) {
            nativeValue = null;
        } else if (value instanceof Collection) {
            Collection existingColl = (Collection)value;
            List<Object> nativeColl = new ArrayList<Object>(existingColl.size());
            for(Object item : existingColl) {
                nativeColl.add(getSimpleNativePropertyValue(item, mappingContext));
            }
            nativeValue = nativeColl;
        } else if (value instanceof Map) {
            Map<String, Object> existingMap = (Map)value;
            Map<String, Object> newMap = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry :existingMap.entrySet()){
                newMap.put(entry.getKey(), getSimpleNativePropertyValue(entry.getValue(), mappingContext));
            }
            nativeValue = newMap;
        } else {
            nativeValue = toNativeSimplePropertyValue(value, mappingContext);
        }
        return nativeValue;
    }

    private static Object toNativeSimplePropertyValue(Object item, MappingContext mappingContext) {
        Object toAdd;
        if (item != null) {
            if (MongoMappingContext.MONGO_NATIVE_TYPES.contains(item.getClass().getName())) {
                // easy case, no conversion required.
                toAdd = item;
            } else {
                ConversionService conversionService = mappingContext.getConversionService();
                // go for toInteger or toString.
                if (conversionService.canConvert(item.getClass(), Integer.class)) {
                    toAdd = conversionService.convert(item, Integer.class);
                } else if (conversionService.canConvert(item.getClass(), String.class)) {
                    toAdd = conversionService.convert(item, String.class);
                } else {
                    // fall back if no explicit converter is registered, good for URL, Locale, etc.
                    toAdd = item.toString();
                }
            }
        } else {
            toAdd = null;
        }
        return toAdd;
    }

    @Override
    protected PersistentEntity discriminatePersistentEntity(PersistentEntity persistentEntity, DBObject nativeEntry) {
        final Object o = nativeEntry.get(MONGO_CLASS_FIELD);
        if (o != null) {
            final String className = o.toString();
            final PersistentEntity childEntity = getMappingContext().getChildEntityByDiscriminator(persistentEntity.getRootEntity(), className);
            if (childEntity != null) {
                return childEntity;
            }
        }
        return super.discriminatePersistentEntity(persistentEntity, nativeEntry);
    }

    @Override
    protected DBObject retrieveEntry(final PersistentEntity persistentEntity,
            String family, final Serializable key) {
        return mongoTemplate.execute(new DbCallback<DBObject>() {
            public DBObject doInDB(DB con) throws MongoException, DataAccessException {
                DBCollection dbCollection = con.getCollection(getCollectionName(persistentEntity));
                return dbCollection.findOne(createDBObjectWithKey(key));
            }
        });
    }

    private DBObject removeNullEntries(DBObject nativeEntry) {
        for(String key : new HashSet<String>(nativeEntry.keySet())) {
            Object o = nativeEntry.get(key);
            if(o == null) {
                nativeEntry.removeField(key);
            } else if(o instanceof Object[]) {
                for(Object o2 : (Object[])o) {
                    if(o2 instanceof DBObject) {
                        removeNullEntries((DBObject)o2);
                    }
                }
            } else if(o instanceof List) {
                for(Object o2 : (List)o) {
                    if(o2 instanceof DBObject) {
                        removeNullEntries((DBObject)o2);
                    }
                }
            } else if(o instanceof DBObject) {
                removeNullEntries((DBObject)o);
            }
        }
        return nativeEntry;
    }

    @Override
    protected Object storeEntry(final PersistentEntity persistentEntity, final EntityAccess entityAccess,
                                final Object storeId, final DBObject nativeEntry) {
        return mongoTemplate.execute(new DbCallback<Object>() {
            public Object doInDB(DB con) throws MongoException, DataAccessException {
                removeNullEntries(nativeEntry);
                nativeEntry.put(MONGO_ID_FIELD, storeId);
                return nativeEntry.get(MONGO_ID_FIELD);
            }
        });
    }

    public String getCollectionName(PersistentEntity persistentEntity) {
        return getCollectionName(persistentEntity, null);
    }

    private String getCollectionName(PersistentEntity persistentEntity, DBObject nativeEntry) {
        @SuppressWarnings("hiding") String collectionName;
        if (persistentEntity.isRoot()) {
            MongoSession mongoSession = (MongoSession) getSession();
            collectionName = mongoSession.getCollectionName(persistentEntity);
        }
        else {
            MongoSession mongoSession = (MongoSession) getSession();
            collectionName = mongoSession.getCollectionName(persistentEntity.getRootEntity());
        }
        return collectionName;
    }

    private DBObject modifyNullsToUnsets(DBObject nativeEntry) {
        DBObject unsets = new BasicDBObject();
        DBObject sets = new BasicDBObject();
        for(String key : nativeEntry.keySet()) {
            Object o = nativeEntry.get(key);
            if(o == null) {
                unsets.put(key, 1);
            } else if("_id".equals(key)) {
            } else if(o instanceof Object[]) {
                sets.put(key, o);
                for(Object o2 : (Object[])o) {
                    if(o2 instanceof DBObject) {
                        removeNullEntries((DBObject)o2);
                    }
                }
            } else if(o instanceof List) {
                sets.put(key, o);
                for(Object o2 : (List)o) {
                    if(o2 instanceof DBObject) {
                        removeNullEntries((DBObject)o2);
                    }
                }
            } else if(o instanceof DBObject) {
                sets.put(key, removeNullEntries((DBObject)o));
            } else {
                sets.put(key, o);
            }
        }
        DBObject newEntry = new BasicDBObject();
        newEntry.put("$set", sets);
        if(!unsets.keySet().isEmpty())
            newEntry.put("$unset", unsets);
        return newEntry;
    }


    @Override
    public void updateEntry(final PersistentEntity persistentEntity, final EntityAccess ea,
            final Object key, final DBObject entry) {
        mongoTemplate.execute(new DbCallback<Object>() {
            public Object doInDB(DB con) throws MongoException, DataAccessException {
                @SuppressWarnings("hiding") String collectionName = getCollectionName(persistentEntity, entry);
                DBCollection dbCollection = con.getCollection(collectionName);
                DBObject dbo = createDBObjectWithKey(key);

                if (isVersioned(ea)) {
                    // TODO this should be done with a CAS approach if possible
                    DBObject previous = dbCollection.findOne(dbo);
                    checkVersion(ea, previous, persistentEntity, key);
                }

                DBObject newEntry = modifyNullsToUnsets(entry);

                MongoSession mongoSession = (MongoSession) session;
                WriteConcern writeConcern = mongoSession.getDeclaredWriteConcern(getPersistentEntity());
                if(writeConcern != null)
                    dbCollection.update(dbo, newEntry, false, false, writeConcern);
                else
                    dbCollection.update(dbo, newEntry, false, false);
                return null;
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void setManyToMany(PersistentEntity persistentEntity, Object obj,
            DBObject nativeEntry, ManyToMany manyToMany, Collection associatedObjects,
            Map<Association, List<Serializable>> toManyKeys) {

        List ids = new ArrayList();
        if (associatedObjects != null) {
            for (Object o : associatedObjects) {
                if (o == null) {
                    ids.add(null);
                }
                else {
                    PersistentEntity childPersistentEntity =
                        getMappingContext().getPersistentEntity(o.getClass().getName());
                    EntityAccess entityAccess = createEntityAccess(childPersistentEntity, o);
                    ids.add(entityAccess.getIdentifier());
                }
            }
        }

        nativeEntry.put(manyToMany.getName() + "_$$manyToManyIds", ids);
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Collection getManyToManyKeys(PersistentEntity persistentEntity, Object object,
            Serializable nativeKey, DBObject nativeEntry, ManyToMany manyToMany) {
        return (Collection)nativeEntry.get(manyToMany.getName() + "_$$manyToManyIds");
    }

    protected void checkVersion(final EntityAccess ea, final DBObject previous,
                                final PersistentEntity persistentEntity, final Object key) {
        Object oldVersion = previous != null ? previous.get("version") : null;
        Object currentVersion = ea.getProperty("version");
        if (Number.class.isAssignableFrom(ea.getPropertyType("version"))) {
            oldVersion = oldVersion != null ? ((Number)oldVersion).longValue() : oldVersion;
            currentVersion = currentVersion != null ? ((Number)currentVersion).longValue() : currentVersion;
        }
        if (oldVersion != null && currentVersion != null && !oldVersion.equals(currentVersion)) {
            throw new OptimisticLockingException(persistentEntity, key);
        }
        incrementVersion(ea);
    }

    @Override
    protected void deleteEntries(String family, final List<Object> keys) {
        mongoTemplate.execute(new DbCallback<Object>() {
            public Object doInDB(DB con) throws MongoException, DataAccessException {
                @SuppressWarnings("hiding") String collectionName = getCollectionName(getPersistentEntity());
                DBCollection dbCollection = con.getCollection(collectionName);

                MongoSession mongoSession = (MongoSession) getSession();
                MongoQuery query = mongoSession.createQuery(getPersistentEntity().getJavaClass());
                query.in(getPersistentEntity().getIdentity().getName(), keys);

                dbCollection.remove(query.getMongoQuery());

                return null;
            }
        });
    }

    protected DBObject createDBObjectWithKey(Object key) {
        DBObject dbo = new BasicDBObject();
        if (hasNumericalIdentifier || hasStringIdentifier) {
            dbo.put(MONGO_ID_FIELD, key);
        }
        else {
            if (key instanceof ObjectId) {
                dbo.put(MONGO_ID_FIELD, key);
            }
            else {
                dbo.put(MONGO_ID_FIELD, new ObjectId(key.toString()));
            }
        }
        return dbo;
    }

    @Override
    public boolean isDirty(Object instance, Object entry) {
        if (super.isDirty(instance, entry)) {
            return true;
        }

        DBObject dbo = (DBObject)entry;
        PersistentEntity entity = getPersistentEntity();

        EntityAccess entityAccess = createEntityAccess(entity, instance, dbo);

        DBObject cached = (DBObject)((SessionImplementor<?>)getSession()).getCachedEntry(
                entity, (Serializable)entityAccess.getIdentifier(), true);

        return !dbo.equals(cached);
    }

    @SuppressWarnings("rawtypes")
    private class MongoAssociationIndexer implements AssociationIndexer {
        private DBObject nativeEntry;
        private Association association;
        @SuppressWarnings("hiding") private MongoSession session;
        private boolean isReference = true;

        public MongoAssociationIndexer(DBObject nativeEntry, Association association, MongoSession session) {
            this.nativeEntry = nativeEntry;
            this.association = association;
            this.session = session;
            this.isReference = isReference(association);
        }

        public void index(final Object primaryKey, final List foreignKeys) {
            // if the association is a unidirectional one-to-many we store the keys
            // embedded in the owning entity, otherwise we use a foreign key
            if (!association.isBidirectional()) {
                mongoTemplate.execute(new DbCallback<Object>() {
                    public Object doInDB(DB db) throws MongoException, DataAccessException {
                        List dbRefs = new ArrayList();
                        for (Object foreignKey : foreignKeys) {
                            if(isReference) {
                                dbRefs.add(new DBRef(db, getCollectionName(association.getAssociatedEntity()), foreignKey));
                            }
                            else {
                                dbRefs.add(foreignKey);
                            }
                        }
                        nativeEntry.put(association.getName(), dbRefs);

                        if(primaryKey != null) {
                            final DBCollection collection = db.getCollection(getCollectionName(association.getOwner()));
                            DBObject query = new BasicDBObject(MONGO_ID_FIELD, primaryKey);
                            collection.update(query, nativeEntry);
                        }
                        return null;
                    }
                });
            }

        }

        @SuppressWarnings("unchecked")
        public List query(Object primaryKey) {
            // for a unidirectional one-to-many we use the embedded keys
            if (!association.isBidirectional()) {
                final Object indexed = nativeEntry.get(association.getName());
                if (indexed instanceof Collection) {
                    if (indexed instanceof List) return (List) indexed;
                    return new ArrayList((Collection)indexed);
                }
                return Collections.emptyList();
            }
            // for a bidirectional one-to-many we use the foreign key to query the inverse side of the association
            Association inverseSide = association.getInverseSide();
            Query query = session.createQuery(association.getAssociatedEntity().getJavaClass());
            query.eq(inverseSide.getName(), primaryKey);
            query.projections().id();
            return query.list();
        }

        public PersistentEntity getIndexedEntity() {
            return association.getAssociatedEntity();
        }

        public void index(Object primaryKey, Object foreignKey) {
            // TODO: Implement indexing of individual entities
        }
    }
}
