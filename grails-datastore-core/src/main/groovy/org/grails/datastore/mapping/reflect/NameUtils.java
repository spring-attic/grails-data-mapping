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
package org.grails.datastore.mapping.reflect;

import java.beans.Introspector;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class NameUtils {

    private static final String PROPERTY_SET_PREFIX = "set";

    /**
     * Retrieves the name of a setter for the specified property name
     * @param propertyName The property name
     * @return The setter equivalent
     */
    public static String getSetterName(String propertyName) {
        return PROPERTY_SET_PREFIX+propertyName.substring(0,1).toUpperCase()+ propertyName.substring(1);
    }
    /**
     * Converts class name to property name using JavaBean decaplization
     *
     * @param name The class name
     * @return The decapitalized name
     */
    public static String decapitalize(String name) {
        return Introspector.decapitalize(name);
    }

    /**
     * Converts a property name to class name according to the JavaBean convention
     *
     * @param name The property name
     * @return The class name
     */
    public static String capitalize(String name) {
        return name.substring(0,1).toUpperCase() + name.substring(1);
    }
}
