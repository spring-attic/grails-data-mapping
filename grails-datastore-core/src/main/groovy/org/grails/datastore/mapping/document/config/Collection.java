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

package org.grails.datastore.mapping.document.config;

/**
 * Configures how an entity is mapped onto a Document collection
 *
 * @author Graeme Rocher
 */
public class Collection {

    private String name;

    /**
     * The name of the collection
     * @return The name of the collection
     */
    public String getCollection() {
        return name;
    }

    /**
     * Sets the name of the collection
     * @param name The name of the collection
     */
    public void setCollection(String name) {
        this.name = name;
    }
}
