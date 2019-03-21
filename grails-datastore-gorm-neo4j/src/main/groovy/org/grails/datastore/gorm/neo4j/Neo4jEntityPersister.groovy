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
package org.grails.datastore.gorm.neo4j

import org.codehaus.groovy.runtime.NullObject
import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.NotFoundException
import org.neo4j.graphdb.Relationship
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.convert.ConversionException
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationIndexer
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.NativeEntryEntityPersister
import org.grails.datastore.mapping.engine.PropertyValueIndexer
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.query.Query

/**
 * Implementation of {@link org.grails.datastore.mapping.engine.EntityPersister} that uses Neo4j database
 * as backend.
 *
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 */
class Neo4jEntityPersister extends NativeEntryEntityPersister<Node, Long> {

    protected final Logger log = LoggerFactory.getLogger(getClass())

    public static final TYPE_PROPERTY_NAME = "__type__"
    public static final String SUBREFERENCE_PROPERTY_NAME = "__subreference__"

    GraphDatabaseService graphDatabaseService

    Neo4jEntityPersister(MappingContext mappingContext, PersistentEntity entity,
              Session session, ApplicationEventPublisher publisher) {
        super(mappingContext, entity, session, publisher)
        graphDatabaseService = session.nativeInterface
    }

    @Override
    String getEntityFamily() {
        classMapping.entity.toString()
    }

    @Override
    protected void deleteEntry(String family, Long key, entry) {
        Node node = graphDatabaseService.getNodeById(key)
        node.getRelationships(Direction.BOTH).each {
            log.debug "deleting relationship $it.startNode -> $it.endNode : ${it.type.name()}"
            it.delete()
        }
        node.delete()
    }

    @Override
    protected Long generateIdentifier(PersistentEntity persistentEntity, Node entry) {
        entry.id
    }

    @Override
    PropertyValueIndexer getPropertyIndexer(PersistentProperty property) {
        new Neo4jPropertyValueIndexer(persistentProperty: property, graphDatabaseService: graphDatabaseService)
    }

    @Override
    AssociationIndexer getAssociationIndexer(Node nativeEntry, Association association) {
        new Neo4jAssociationIndexer(nativeEntry: nativeEntry, association:association, graphDatabaseService: graphDatabaseService)
    }

    @Override
    protected Node createNewEntry(String family) {
        Node node = graphDatabaseService.createNode()
        node.setProperty(TYPE_PROPERTY_NAME, family)
        Node subreferenceNode = ((Neo4jDatastore)session.datastore).subReferenceNodes[family]
        assert subreferenceNode
        subreferenceNode.createRelationshipTo(node, GrailsRelationshipTypes.INSTANCE)
        log.debug("created node $node.id with class $family")
        node
    }

    @Override
    protected getEntryValue(Node nativeEntry, String property) {
        def result
        Association association = persistentEntity.associations.find { it.name == property }
        if (association) {
            DynamicRelationshipType relname = DynamicRelationshipType.withName(property)

            if (log.debugEnabled) {
                nativeEntry.relationships.each {
                    log.debug("rels $nativeEntry.id  has relationship ${it.startNode.id} -> ${it.endNode.id}, type $it.type")
                }
            }

            if (association instanceof ManyToMany) {
                String relName = association.owningSide ? association.inversePropertyName : association.name
                result = nativeEntry.getRelationships(DynamicRelationshipType.withName(relName)).collect { it.getOtherNode(nativeEntry).id }
            }
            else {
                Relationship rel = nativeEntry.getSingleRelationship(relname, Direction.OUTGOING)
                result = rel ? rel.getOtherNode(nativeEntry).id : null
                log.debug("getting property $property via relationship on $nativeEntry = $result")
            }
        } else {
            result = nativeEntry.getProperty(property, null)
            PersistentProperty pe = discriminatePersistentEntity(persistentEntity, nativeEntry).getPropertyByName(property)
            try {
                result = mappingContext.conversionService.convert(result, pe.type)
            } catch (ConversionException e) {
                log.error("prop $property: $e.message")
                throw e
            }
            log.debug("getting property $property on $nativeEntry = $result")
        }
        result
    }

    @Override
    protected void setEntryValue(Node nativeEntry, String key, value) {
        if (value == null || key == 'id') {
            return
        }

        PersistentProperty persistentProperty = persistentEntity.getPropertyByName(key)
        if (persistentProperty instanceof ManyToMany) {
            // called when loading instances
            DynamicRelationshipType relationshipType = DynamicRelationshipType.withName(key)
            for (item in value) {
                Node endNode = graphDatabaseService.getNodeById(item instanceof Long ? item : item.id)
                for (Relationship rel in nativeEntry.getRelationships(relationshipType, Direction.OUTGOING)) {
                    rel.delete()
                }
                nativeEntry.createRelationshipTo(endNode, relationshipType)
            }
        }
        else if (persistentProperty instanceof Association) {
            log.info("setting $key via relationship to $value, assoc is ${persistentProperty.getClass().superclass.simpleName}, bidi: $persistentProperty.bidirectional, owning: $persistentProperty.owningSide")

            Node endNode = graphDatabaseService.getNodeById(value instanceof Long ? value : value.id)
            DynamicRelationshipType relationshipType = DynamicRelationshipType.withName(key)

            Relationship rel = nativeEntry.getSingleRelationship(relationshipType, Direction.OUTGOING)
            if (rel) {
                if (rel.endNode == endNode) {
                    return // unchanged value
                }
                log.info "deleting relationship $rel.startNode -> $rel.endNode : ${rel.type.name()}"
                rel.delete()
            }

            rel = nativeEntry.createRelationshipTo(endNode, relationshipType)
            log.info("createRelationship $rel.startNode.id -> $rel.endNode.id ($rel.type)")
        }
        else {
            log.debug("setting property $key = $value ${value?.getClass()}")

            if (!isAllowedNeo4jType(value.getClass())) {
                value = mappingContext.conversionService.convert(value, String)
            }
            nativeEntry.setProperty(key, value)
        }
    }

    @Override
    protected Node retrieveEntry(PersistentEntity persistentEntity, String family, Serializable key) {
        try {
            Node node = graphDatabaseService.getNodeById(key)

            def type = node.getProperty(TYPE_PROPERTY_NAME, null)
            switch (type) {
                case null:
                    log.warn("node $key has no property 'type' - maybe a tranaction problem.")
                    return null
                    break
                case family:
                    return node
                    break
                default:
                    //mappingContext.persistentEntities.find
                    Class clazz = Thread.currentThread().contextClassLoader.loadClass(type)
                    persistentEntity.javaClass.isAssignableFrom(clazz) ? node : null
            }
        } catch (NotFoundException e) {
            log.warn("could not retrieve an Node for id $key")
            null
        }
    }

    /**
     * Neo4j does not need to do anything on storeEntry
     * @param persistentEntity The persistent entity
     * @param entityAccess The EntityAccess
     * @param storeId
     * @param nativeEntry The native form. Could be a a ColumnFamily, BigTable Entity etc.
     * @return
     */
    @Override
    protected Long storeEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, Long storeId, Node nativeEntry) {
        assert storeId
        assert nativeEntry
        assert persistentEntity
        log.info "storeEntry $persistentEntity $storeId"
        storeId // TODO: not sure what to do here...
    }

    @Override
    protected void updateEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, Long key, Node entry) {
        if (!entry.hasProperty("version")) {
            return
        }

        long newVersion = entry.getProperty("version") + 1
        entry.setProperty("version", newVersion)
        entityAccess.entity.version = newVersion
    }

    @Override
    protected void deleteEntries(String family, List<Long> keys) {
        log.error("delete $keys")
        throw new UnsupportedOperationException()
    }

    Query createQuery() {
        new Neo4jQuery(session, persistentEntity, this)
    }

    protected boolean isAllowedNeo4jType(Class clazz) {
        switch (clazz) {
            case null:
            case NullObject:
            case String:
            case Integer:
            case Long:
            case Byte:
            case Float:
            case Boolean:
            case String[]:
            case Integer[]:
            case Long[]:
            case Float[]:
                return true
                break
            default:
                return false
        }
    }

    @Override
    protected PersistentEntity discriminatePersistentEntity(PersistentEntity persistentEntity, Node nativeEntry) {
        String className = nativeEntry.getProperty(TYPE_PROPERTY_NAME, null)
        PersistentEntity targetEntity = mappingContext.getPersistentEntity(className)
        for (def entity = targetEntity; entity != persistentEntity || entity == null; entity = entity.parentEntity) {
            assert entity
        }
        targetEntity
    }

    @Override
    protected void setManyToMany(PersistentEntity persistentEntity, obj, Node nativeEntry,
            ManyToMany manyToMany, Collection associatedObjects, Map<Association, List<Serializable>> toManyKeys) {

        toManyKeys.put manyToMany, session.persist(associatedObjects)
    }

    @Override
    protected Collection getManyToManyKeys(PersistentEntity persistentEntity, object,
            Serializable nativeKey, Node nativeEntry, ManyToMany manyToMany) {

        String relName = manyToMany.owningSide ? manyToMany.inversePropertyName : manyToMany.name
        nativeEntry.getRelationships(DynamicRelationshipType.withName(relName)).collect { it.getOtherNode(nativeEntry).id }
    }
}
