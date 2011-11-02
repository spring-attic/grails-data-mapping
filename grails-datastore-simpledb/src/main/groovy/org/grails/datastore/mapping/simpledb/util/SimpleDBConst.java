/* Copyright (C) 2011 SpringSource
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
package org.grails.datastore.mapping.simpledb.util;

/**
 * Various constants for SimpleDB support.
 *
 * @author Roman Stepanenko
 * @since 0.1
 */
public class SimpleDBConst {

    private SimpleDBConst() {
        // don't instantiate
    }

    public static final String PROP_ID_GENERATOR_TYPE = "type";
    public static final String PROP_ID_GENERATOR_TYPE_HILO = "hilo";
    public static final String PROP_ID_GENERATOR_TYPE_UUID = "uuid"; //used by default
    public static final String PROP_ID_GENERATOR_MAX_LO = "maxLo";
    public static final int PROP_ID_GENERATOR_MAX_LO_DEFAULT_VALUE = 1000;
    public static final String ID_GENERATOR_HI_LO_DOMAIN_NAME = "HiLo"; //in which domain will HiLo store the counters, this domain name might be prefixed, as for all other domains
    public static final String ID_GENERATOR_HI_LO_ATTRIBUTE_NAME = "nextHi";

    public static final String PROP_SHARDING_ENABLED = "enabled";

    /**
     * What must be specified in mapping as a value of 'mapWith' to map the
     * domain class with SimpleDB gorm plugin:
     * <pre>
     * class DomPerson {
     *      String id
     *      String firstName
     *      static mapWith = "simpledb"
     * }
     * </pre>
     */
    public static final String SIMPLE_DB_MAP_WITH_VALUE = "simpledb";

}
