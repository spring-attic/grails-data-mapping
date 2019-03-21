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

import org.neo4j.graphdb.GraphDatabaseService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.grails.datastore.mapping.transactions.Transaction

/**
 * wrapping a Neo4j {@link org.neo4j.graphdb.Transaction} into a Spring data mapping {@link Transaction}
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 */
class Neo4jTransaction implements Transaction {

    protected final Logger log = LoggerFactory.getLogger(getClass())

    GraphDatabaseService graphDatabaseService
    org.neo4j.graphdb.Transaction nativeTransaction
    boolean active = true

    Neo4jTransaction(GraphDatabaseService graphDatabaseService) {
        this.graphDatabaseService = graphDatabaseService
        nativeTransaction = graphDatabaseService.beginTx()
        log.debug "new: $nativeTransaction"
    }

    void commit() {
        log.debug "commit $nativeTransaction"
        nativeTransaction.success()
        nativeTransaction.finish()
        //nativeTransaction = graphDatabaseService.beginTx()
//        active = false
    }

    void rollback() {
        log.debug "rollback $nativeTransaction"
        nativeTransaction.failure()
        nativeTransaction.finish()
        //nativeTransaction = graphDatabaseService.beginTx()
//        active = false
    }

    Object getNativeTransaction() {
        nativeTransaction
    }

    boolean isActive() {
        active
    }

    void setTimeout(int timeout) {
        throw new UnsupportedOperationException()
    }
}
