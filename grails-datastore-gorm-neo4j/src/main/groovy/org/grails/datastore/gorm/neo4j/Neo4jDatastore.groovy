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

import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.Transaction
import org.neo4j.kernel.EmbeddedGraphDatabase
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ConfigurableApplicationContext
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Datastore implementation for Neo4j backend
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 * TODO: refactor constructors to be groovier
 */
class Neo4jDatastore extends AbstractDatastore implements InitializingBean {

    GraphDatabaseService graphDatabaseService
    Map<Class, Node> subReferenceNodes
    String storeDir

    /**
     * only to be called during testing
     * @return
     */
    Neo4jDatastore() {
        this(new Neo4jMappingContext(), null, null)
    }

    Neo4jDatastore(Neo4jMappingContext mappingContext, ConfigurableApplicationContext ctx, GraphDatabaseService graphDatabaseService) {
        super(mappingContext, Collections.<String,String>emptyMap(), ctx)
        this.graphDatabaseService = graphDatabaseService

/*        mappingContext.getConverterRegistry().addConverter(new Converter<String, ObjectId>() {
            ObjectId convert(String source) {
                return new ObjectId(source)
            }
        })

        mappingContext.getConverterRegistry().addConverter(new Converter<ObjectId, String>() {
            String convert(ObjectId source) {
                return source.toString()
            }
        })*/

    }

    Neo4jDatastore(Neo4jMappingContext mappingContext, GraphDatabaseService graphDatabaseService) {
        this(mappingContext, null, graphDatabaseService)
    }

    @Override
    protected Session createSession(Map<String, String> connectionDetails) {
        new Neo4jSession(this, mappingContext, applicationEventPublisher)
    }

    void afterPropertiesSet() {
        if (!graphDatabaseService) {
            assert storeDir
            graphDatabaseService = new EmbeddedGraphDatabase(storeDir)
        }
        initializeConverters(mappingContext)
        findOrCreateSubReferenceNodes()
    }

    protected Node createSubReferenceNode(name) {
        Transaction tx = graphDatabaseService.beginTx()
        try {
            Node subReferenceNode = graphDatabaseService.createNode()
            subReferenceNode.setProperty(Neo4jEntityPersister.SUBREFERENCE_PROPERTY_NAME, name)
            graphDatabaseService.referenceNode.createRelationshipTo(subReferenceNode, GrailsRelationshipTypes.SUBREFERENCE)
            tx.success()
            return subReferenceNode
        } finally {
            tx.finish()
        }
    }

    protected void findOrCreateSubReferenceNodes() {
        subReferenceNodes = [:]
        Node referenceNode = graphDatabaseService.referenceNode
        for (Relationship rel in referenceNode.getRelationships(GrailsRelationshipTypes.SUBREFERENCE, Direction.OUTGOING)) {
            Node endNode = rel.endNode
            Class clazz = endNode.getProperty(Neo4jEntityPersister.SUBREFERENCE_PROPERTY_NAME)
            subReferenceNodes[clazz] = endNode
        }

        for (PersistentEntity pe in mappingContext.persistentEntities) {
            if (!subReferenceNodes.containsKey(pe.name)) {
                subReferenceNodes[pe.name] = createSubReferenceNode(pe.name)
            }
        }
    }
}
