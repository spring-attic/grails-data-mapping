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
package org.grails.datastore.mapping.simpledb.model.types;

import org.springframework.core.GenericTypeResolver;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.converter.GenericConverter;
import org.grails.datastore.mapping.model.types.BasicTypeConverterRegistrar;
import org.grails.datastore.mapping.simpledb.util.SimpleDBConst;

import com.amazonaws.services.simpledb.util.SimpleDBUtils;

/**
 * A registrar that registers type converters used for SimpleDB. For example,
 * numeric types are padded with zeros because AWS SimpleDB stores everything as
 * a string and without padding ordering of numerics would not work.
 *
 * @author Roman Stepanenko
 * @since 0.1
 */
public class SimpleDBTypeConverterRegistrar extends BasicTypeConverterRegistrar {

    public static final Converter<Integer, String> INTEGER_TO_STRING_CONVERTER = new Converter<Integer, String>() {
        public String convert(Integer source) {
            return SimpleDBUtils.encodeZeroPadding(source, SimpleDBConst.PADDING_INT_DEFAULT);
        }
    };

    public static final Converter<Long, String> LONG_TO_STRING_CONVERTER = new Converter<Long, String>() {
        public String convert(Long source) {
            return SimpleDBUtils.encodeZeroPadding(source, SimpleDBConst.PADDING_LONG_DEFAULT);
        }
    };

    @Override
    public void register(ConverterRegistry registry) {
        //we use most of the standard's converters
        super.register(registry);

        overwrite(registry, INTEGER_TO_STRING_CONVERTER);
        overwrite(registry, LONG_TO_STRING_CONVERTER);
    }

    protected void overwrite(ConverterRegistry registry, @SuppressWarnings("rawtypes") Converter converter) {
        //get type info for the specified converter
        GenericConverter.ConvertiblePair typeInfo = getRequiredTypeInfo(converter, Converter.class);
        if (typeInfo == null) {
            throw new IllegalArgumentException(
                  "Unable to the determine sourceType <S> and targetType <T> which " +
                  "your Converter<S, T> converts between; declare these generic types. Converter class: " +
                  converter.getClass().getName());
        }

        //now remove converters that we will overwrite for SimpleDB
        registry.removeConvertible(typeInfo.getSourceType(), typeInfo.getTargetType());

        //now add
        registry.addConverter(converter);
    }

    private GenericConverter.ConvertiblePair getRequiredTypeInfo(Object converter, Class<?> genericIfc) {
        Class<?>[] args = GenericTypeResolver.resolveTypeArguments(converter.getClass(), genericIfc);
        return (args != null ? new GenericConverter.ConvertiblePair(args[0], args[1]) : null);
    }
}
