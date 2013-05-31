/* Copyright (C) 2010 SpringSource
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
package org.grails.datastore.mapping.model.types.conversion

import groovy.transform.CompileStatic

import org.springframework.core.convert.converter.Converter

/**
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 */
@CompileStatic
class StringToURLConverter implements Converter<String, URL> {
    URL convert(String source) {
        new URL(source)
    }
}
