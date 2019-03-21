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

package org.grails.datastore.mapping.core.impl;

import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Provides default implementation for most of the methods in the {@link PendingInsert} interafce
 *
 * @param <E> The native entry to persist
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class PendingInsertAdapter<E, K> extends PendingOperationAdapter<E, K> implements PendingInsert<E, K>{

    private EntityAccess entityAccess;

    public PendingInsertAdapter(PersistentEntity entity, K nativeKey, E nativeEntry, EntityAccess ea) {
        super(entity, nativeKey, nativeEntry);
        this.entityAccess = ea;
    }

    public EntityAccess getEntityAccess() {
        return entityAccess;
    }
}
