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
package org.grails.datastore.mapping.keyvalue.engine;

import java.util.HashMap;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class KeyValueEntry extends HashMap<String, Object> {

    private String family;

    public KeyValueEntry(String family) {
        this.family = family;
    }

    public String getFamily() {
        return family;
    }
}
