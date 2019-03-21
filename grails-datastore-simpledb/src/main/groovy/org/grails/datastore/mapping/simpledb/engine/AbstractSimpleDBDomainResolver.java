/* Copyright (C) 2011 SpringSource
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
package org.grails.datastore.mapping.simpledb.engine;

/**
 * @author Roman Stepanenko
 */
public abstract class AbstractSimpleDBDomainResolver implements SimpleDBDomainResolver {

    protected String entityFamily;
    protected String domainNamePrefix;

    public AbstractSimpleDBDomainResolver(String entityFamily, String domainNamePrefix) {
        this.entityFamily = entityFamily;
        this.domainNamePrefix = domainNamePrefix;
        if (domainNamePrefix != null) {
            this.entityFamily = domainNamePrefix + this.entityFamily;
        }
    }
}
