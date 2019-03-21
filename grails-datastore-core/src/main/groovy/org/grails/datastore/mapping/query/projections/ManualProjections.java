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
package org.grails.datastore.mapping.query.projections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.order.ManualEntityOrdering;

/**
 * Implements common projections in-memory given a set of results. Not all
 * NoSQL datastores support projections like SQL min(..), max(..) etc.
 * This class provides support for those that don't.
 */
public class ManualProjections {

    PersistentEntity entity;
    private ManualEntityOrdering order;

    public ManualProjections(PersistentEntity entity) {
        this.entity = entity;
        this.order = new ManualEntityOrdering(entity);
    }

    /**
     * Calculates the minimum value of a property
     *
     * @param results The results
     * @param property The property to calculate
     * @return The minimum value or null if there are no results
     */
    public Object min(Collection results, String property) {
        if (results == null || results.isEmpty()) {
            return null;
        }

        final List sorted = order.applyOrder(new ArrayList(results), Query.Order.asc(property));
        final Object o = sorted.get(0);
        if (entity.isInstance(o)) {
            return new EntityAccess(entity, o).getProperty(property);
        }
        return o;
    }

   /**
     * Calculates the maximum value of a property
     *
     * @param results The results
     * @param property The property to calculate
     * @return The maximum value or null if there are no results
     */
    public Object max(Collection results, String property) {
        if (results == null || results.isEmpty()) {
            return null;
        }

        final List sorted = order.applyOrder(new ArrayList(results), Query.Order.asc(property));
        final Object o = sorted.get(results.size()-1);
        if (entity.isInstance(o)) {
            return new EntityAccess(entity, o).getProperty(property);
        }
        return o;
    }

    /**
     * Obtains a properties value from the results
     *
     * @param results The results
     * @param property The property
     * @return A list of results
     */
    public List property(Collection results, String property) {
        List projectedResults = new ArrayList();
        if (results == null || results.isEmpty()) {
            return projectedResults;
        }

        for (Object o : results) {
            EntityAccess ea = new EntityAccess(entity, o);
            if (entity.isInstance(o)) {
                projectedResults.add(ea.getProperty(property));
            }
            else {
                projectedResults.add(null);
            }
        }

        return projectedResults;
    }
}
