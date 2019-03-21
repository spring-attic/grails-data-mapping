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
package org.grails.datastore.gorm.support;

import javax.persistence.FlushModeType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.support.PersistenceContextInterceptor;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.transactions.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @since 1.0
 */
public class DatastorePersistenceContextInterceptor implements PersistenceContextInterceptor {

    private static final Log LOG = LogFactory.getLog(DatastorePersistenceContextInterceptor.class);
    protected Datastore datastore;
    protected boolean participate;

    public DatastorePersistenceContextInterceptor(Datastore datastore) {
        this.datastore = datastore;
    }

    public void init() {
      if (TransactionSynchronizationManager.getResource(datastore) != null) {
            // Do not modify the Session: just set the participate flag.
            participate = true;
        }
        else {
            LOG.debug("Opening single Datastore session in DatastorePersistenceContextInterceptor");
            Session session = getSession();
            session.setFlushMode(FlushModeType.AUTO);
            try {
                DatastoreUtils.bindSession(session);
            } catch (IllegalStateException e) {
                // ignore, already bound
            }
        }
    }

    protected Session getSession() {
        return DatastoreUtils.getSession(datastore, true);
    }

    public void destroy() {
        if (participate) {
            return;
        }

        // single session mode
        if (TransactionSynchronizationManager.getResource(datastore) != null) {
            SessionHolder holder = (SessionHolder)TransactionSynchronizationManager.unbindResource(datastore);
            LOG.debug("Closing single Datastore session in DatastorePersistenceContextInterceptor");
            try {
                Session session = holder.getSession();
                DatastoreUtils.closeSession(session);
            }
            catch (RuntimeException ex) {
                LOG.error("Unexpected exception on closing Datastore Session", ex);
            }
        }
    }

    public void disconnect() {
        destroy();
    }

    public void reconnect() {
        init();
    }

    public void flush() {
        getSession().flush();
    }

    public void clear() {
        getSession().clear();
    }

    public void setReadOnly() {
        getSession().setFlushMode(FlushModeType.COMMIT);
    }

    public void setReadWrite() {
        getSession().setFlushMode(FlushModeType.AUTO);
    }

    public boolean isOpen() {
        try {
            return DatastoreUtils.doGetSession(datastore, false).isConnected();
        }
        catch (IllegalStateException e) {
            return false;
        }
    }
}
