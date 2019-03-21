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

import org.grails.datastore.mapping.config.AbstractGormMappingFactory
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.keyvalue.mapping.config.Family

/**
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 */
class GraphGormMappingFactory extends AbstractGormMappingFactory {

    @Override
    protected Class getPropertyMappedFormType() {
        Property
    }

    @Override
    protected Class getEntityMappedFormType() {
        Family
    }
}
