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
package org.grails.datastore.mapping.engine;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.convert.ConversionService;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.IdentityMapping;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.springframework.util.ReflectionUtils;

/**
 * Class used to access properties of an entity. Also responsible for
 * any conversion from source to target types.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class EntityAccess {

    private static final Set EXCLUDED_PROPERTIES = new HashSet(Arrays.asList("class", "metaClass"));

    protected Object entity;
    private BeanWrapper beanWrapper;
    private PersistentEntity persistentEntity;

    public EntityAccess(PersistentEntity persistentEntity, Object entity) {
        this.entity = entity;
        this.persistentEntity = persistentEntity;
        beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
    }

    public Object getEntity() {
        return entity;
    }

    public void setConversionService(ConversionService conversionService) {
        beanWrapper.setConversionService(conversionService);
    }

    public Object getProperty(String name) {
        return beanWrapper.getPropertyValue(name);
    }

    public Class getPropertyType(String name) {
        return beanWrapper.getPropertyType(name);
    }

    public void setProperty(String name, Object value) {
        beanWrapper.setPropertyValue(name, value);
    }

    public Object getIdentifier() {
        String idName = getIdentifierName(persistentEntity.getMapping());
        if (idName != null) {
            return getProperty(idName);
        }
        return getProperty(persistentEntity.getIdentity().getName());
    }

    public void setIdentifier(Object id) {
        String idName = getIdentifierName(persistentEntity.getMapping());
        setProperty(idName, id);
    }

    protected String getIdentifierName(ClassMapping cm) {
        final IdentityMapping identifier = cm.getIdentifier();
        if (identifier != null && identifier.getIdentifierName() != null) {
            return identifier.getIdentifierName()[0];
        }
        return null;
    }

    public String getIdentifierName() {
        return getIdentifierName(persistentEntity.getMapping());
    }

    public PersistentEntity getPersistentEntity() {
        return persistentEntity;
    }

    public void setPropertyNoConversion(String name, Object value) {
        final PropertyDescriptor pd = beanWrapper.getPropertyDescriptor(name);
        if (pd == null) {
            return;
        }
        final Method writeMethod = pd.getWriteMethod();
        if (writeMethod != null) {
            ReflectionUtils.invokeMethod(writeMethod, beanWrapper.getWrappedInstance(), value);
        }
    }

    /**
     * Refreshes the object from entity state.
     */
    public void refresh() {
        final PropertyDescriptor[] descriptors = beanWrapper.getPropertyDescriptors();
        for (PropertyDescriptor descriptor : descriptors) {
            final String name = descriptor.getName();
            if (EXCLUDED_PROPERTIES.contains(name)) {
                continue;
            }

            if (!beanWrapper.isReadableProperty(name) || !beanWrapper.isWritableProperty(name)) {
                continue;
            }

            Object newValue = getProperty(name);
            setProperty(name, newValue);
        }
    }
}
