/*
 * Copyright 2004-2010 the original author or authors.
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
package org.codehaus.groovy.grails.orm.hibernate

import groovy.transform.CompileStatic

import org.hibernate.FlushMode
import org.springframework.orm.hibernate4.HibernateTransactionManager
import org.springframework.orm.hibernate4.HibernateTransactionManager.HibernateTransactionObject
import org.springframework.transaction.TransactionDefinition

/**
 * Extends the standard class to always set the flush mode to manual when in a read-only transaction.
 *
 * @author Burt Beckwith
 */
class GrailsHibernateTransactionManager extends HibernateTransactionManager {

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin transaction, definition

        if (definition.isReadOnly()) {
            // transaction is HibernateTransactionManager.HibernateTransactionObject private class instance
            transaction.sessionHolder?.session?.with {
                // always set to manual; the base class doesn't because the OSIVI has already registered a session
                flushMode = FlushMode.MANUAL
                // set session to load entities in read-only mode
                defaultReadOnly = true
            }
        }
    }
}
