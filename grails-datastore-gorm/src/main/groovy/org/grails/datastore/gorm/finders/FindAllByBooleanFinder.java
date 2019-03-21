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

package org.grails.datastore.gorm.finders;

import org.grails.datastore.mapping.core.Datastore;

/**
 * The "findAll<booleanProperty>By*" static persistent method. This method allows querying for
 * instances of grails domain classes based on a boolean property and any other arbitrary
 * properties.
 *
 * eg.
 * Account.findAllActiveByHolder("Joe Blogs"); // Where class "Account" has a properties called "active" and "holder"
 * Account.findAllActiveByHolderAndBranch("Joe Blogs", "London"); // Where class "Account" has a properties called "active', "holder" and "branch"
 *
 * In both of those queries, the query will only select Account objects where active=true.
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 */
public class FindAllByBooleanFinder extends FindAllByFinder{
    private static final String METHOD_PATTERN = "(findAll)((\\w+)(By)([A-Z]\\w*)|(\\w+))";
    public FindAllByBooleanFinder(Datastore datastore) {
        super(datastore);
        setPattern(METHOD_PATTERN);
    }
}
