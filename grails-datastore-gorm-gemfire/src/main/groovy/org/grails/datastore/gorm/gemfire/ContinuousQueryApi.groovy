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
package org.grails.datastore.gorm.gemfire

import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.gemfire.GemfireDatastore
import org.grails.datastore.mapping.gemfire.query.GemfireQuery
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.util.Assert

import com.gemstone.gemfire.cache.query.CqAttributes
import com.gemstone.gemfire.cache.query.CqAttributesFactory
import com.gemstone.gemfire.cache.query.CqEvent
import com.gemstone.gemfire.cache.query.CqListener

/**
 * Extended API for doing Continous queries in Gemfire
 */
class ContinuousQueryApi {

    final PersistentEntity entity
    final GemfireDatastore gemfire

    private dynamicFinders

    ContinuousQueryApi(PersistentEntity entity, GemfireDatastore gemfire, List<FinderMethod> finders) {
        this.entity = entity
        this.gemfire = gemfire
        this.dynamicFinders = finders
    }

    def invokeMethod(String methodName, args) {

        def gemfirePool = gemfire.gemfirePool
        Assert.notNull(gemfirePool, "Cannot invoke a continuous query without an appropriately initialized Gemfire Pool")

        FinderMethod method = dynamicFinders.find { FinderMethod f -> f.isMethodMatch(methodName) }
        if (!method || !args || !(args[-1] instanceof Closure) || !(method instanceof DynamicFinder)) {
            throw new MissingMethodException(methodName, entity.javaClass, args)
        }

        DynamicFinder dynamicFinder = method

        def invocation = dynamicFinder.createFinderInvocation(entity.javaClass, methodName, null, args)
        // TODO not sure if current session makes sense for continuous query
        GemfireQuery q = dynamicFinder.buildQuery(invocation, gemfire.currentSession)
        def queryString = q.getQueryString()

        def queryService = gemfirePool.getQueryService()

        CqAttributesFactory cqf = new CqAttributesFactory()
        def listeners = [new ClosureInvokingCqListener(args[-1])] as CqListener[]
        cqf.initCqListeners(listeners)
        CqAttributes attrs = cqf.create()

        def cqName = "${entity.name}.${methodName}(${args[0..-2].join(',')})"
        def continuousQuery = queryService.newCq(cqName,queryString, attrs)

        continuousQuery.execute()
        gemfire.addContinuousQuery(continuousQuery)
        return continuousQuery
    }
}

class ClosureInvokingCqListener implements CqListener {

    Closure callable

    ClosureInvokingCqListener(Closure callable) {
        this.callable = callable
    }

    void onEvent(CqEvent cqEvent) {
        callable?.call(cqEvent)
    }

    void onError(CqEvent cqEvent) {
        callable?.call(cqEvent)
    }

    void close() {
        // do nothing
    }
}
