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
package org.grails.datastore.mapping.transactions;

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

import org.grails.datastore.mapping.core.Session;
import org.springframework.transaction.support.ResourceHolderSupport;

/**
 * Holds a reference to one or more sessions.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class SessionHolder extends ResourceHolderSupport {

    private Deque<Session> sessions = new LinkedBlockingDeque<Session>();
    private Transaction transaction;

    public SessionHolder(Session session) {
        sessions.add(session);
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Session getSession() {
        return sessions.peekLast();
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public boolean doesNotHoldNonDefaultSession() {
        return isEmpty();
    }

    public void addSession(Session session) {
        sessions.add(session);
    }

    public void removeSession(Session session) {
        sessions.remove(session);
    }

    public boolean containsSession(Session session) {
        return sessions.contains(session);
    }

    public int size() {
        return sessions.size();
    }

    public Session getValidatedSession() {
        Session session = getSession();
        if (session != null && !session.isConnected()) {
            removeSession(session);
            session = null;
        }
        return session;
    }
}
