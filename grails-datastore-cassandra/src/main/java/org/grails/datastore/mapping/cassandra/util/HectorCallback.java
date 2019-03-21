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
package org.grails.datastore.mapping.cassandra.util;

import me.prettyprint.cassandra.model.HectorException;
import me.prettyprint.cassandra.service.Keyspace;

/**
 * Wraps interaction with Hector
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HectorCallback {
    Object doInHector(Keyspace keyspace) throws HectorException;
}
