/*
 * Copyright 2004-2006 Graeme Rocher
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
package org.codehaus.groovy.grails.orm.hibernate.support;

import grails.validation.DeferredBindingActions;

import java.sql.Connection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.lifecycle.ShutdownOperations;
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.codehaus.groovy.grails.orm.hibernate.metaclass.AbstractSavePersistentMethod;
import org.codehaus.groovy.grails.support.PersistenceContextInterceptor;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.orm.hibernate4.SessionFactoryUtils;
import org.springframework.orm.hibernate4.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Graeme Rocher
 * @since 0.4
 */
public class HibernatePersistenceContextInterceptor implements PersistenceContextInterceptor {

    private static final Log LOG = LogFactory.getLog(HibernatePersistenceContextInterceptor.class);
    private SessionFactory sessionFactory;

    private ThreadLocal<Boolean> participate = new ThreadLocal<Boolean>();
    private ThreadLocal<Integer> nestingCount = new ThreadLocal<Integer>();

    public HibernatePersistenceContextInterceptor() {
        ShutdownOperations.addOperation(new Runnable() {
            public void run() {
                participate.remove();
                nestingCount.remove();
            }
        });
    }

    /* (non-Javadoc)
     * @see org.codehaus.groovy.grails.support.PersistenceContextInterceptor#destroy()
     */
    public void destroy() {
        DeferredBindingActions.clear();
        if (decNestingCount() > 0 || getParticipate()) {
            return;
        }

        try {
            // single session mode
            SessionHolder holder = (SessionHolder)TransactionSynchronizationManager.unbindResource(getSessionFactory());
            LOG.debug("Closing single Hibernate session in GrailsDispatcherServlet");
            try {
                SessionFactoryUtils.closeSession(holder.getSession());
            }
            catch (RuntimeException ex) {
                LOG.error("Unexpected exception on closing Hibernate Session", ex);
            }
        }
        finally {
            AbstractSavePersistentMethod.clearDisabledValidations();
        }
    }

    public void disconnect() {
        try {
            getSession(false).disconnect();
        }
        catch (IllegalStateException e) {
            // no session ignore
        }
    }

    public void reconnect() {
        Session session = getSession();
        Connection connection = ((SessionImplementor)session).getTransactionCoordinator().getJdbcCoordinator().getLogicalConnection().getDistinctConnectionProxy();
        session.reconnect(connection);
    }

    public void flush() {
        getSession().flush();
    }

    public void clear() {
        getSession().clear();
    }

    public void setReadOnly() {
        getSession().setFlushMode(FlushMode.MANUAL);
    }

    public void setReadWrite() {
        getSession().setFlushMode(FlushMode.AUTO);
    }

    public boolean isOpen() {
        try {
            return getSession(false).isOpen();
        }
        catch (IllegalStateException e) {
            return false;
        }
    }

    /* (non-Javadoc)
     * @see org.codehaus.groovy.grails.support.PersistenceContextInterceptor#init()
     */
    public void init() {
        if (incNestingCount() > 1) {
            return;
        }
        SessionFactory sf = getSessionFactory();
        if (TransactionSynchronizationManager.hasResource(sf)) {
            // Do not modify the Session: just set the participate flag.
            setParticipate(true);
        }
        else {
            setParticipate(false);
            LOG.debug("Opening single Hibernate session in HibernatePersistenceContextInterceptor");
            Session session = getSession();
            GrailsHibernateUtil.enableDynamicFilterEnablerIfPresent(sf, session);
            session.setFlushMode(FlushMode.AUTO);
            TransactionSynchronizationManager.bindResource(sf, new SessionHolder(session));
        }
    }

    private Session getSession() {
        return getSession(true);
    }

    private Session getSession(boolean allowCreate) {

        Object value = TransactionSynchronizationManager.getResource(getSessionFactory());
        if (value instanceof Session) {
            return (Session) value;
        }

        if (value instanceof SessionHolder) {
            SessionHolder sessionHolder = (SessionHolder) value;
            return sessionHolder.getSession();
        }

        if (allowCreate) {
            return getSessionFactory().openSession();
        }

        throw new IllegalStateException("No Hibernate Session bound to thread, and configuration does not allow creation of non-transactional one here");
    }

    /**
     * @return the sessionFactory
     */
    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * @param sessionFactory the sessionFactory to set
     */
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private int incNestingCount() {
        Integer current = nestingCount.get();
        int value = (current != null) ? current + 1 : 1;
        nestingCount.set(value);
        return value;
    }

    private int decNestingCount() {
        Integer current = nestingCount.get();
        int value = current == null ? 0 : current - 1;
        if (value < 0) {
            value = 0;
        }
        nestingCount.set(value);
        return value;
    }

    private void setParticipate(boolean flag) {
        participate.set(flag);
    }

    private boolean getParticipate() {
        Boolean ret = participate.get();
        return ret == null ? false : ret;
    }
}
