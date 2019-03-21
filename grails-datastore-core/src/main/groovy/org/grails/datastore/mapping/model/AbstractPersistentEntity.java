/* Copyright 2004-2005 the original author or authors.
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
package org.grails.datastore.mapping.model;

import groovy.lang.Closure;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.grails.datastore.mapping.config.groovy.MappingConfigurationBuilder;
import org.grails.datastore.mapping.core.EntityCreationException;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.OneToMany;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.springframework.util.Assert;

/**
 * Abstract implementation to be subclasses on a per datastore basis
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractPersistentEntity<T> implements PersistentEntity {
    protected Class javaClass;
    protected List<PersistentProperty> persistentProperties;
    protected List<Association> associations;
    protected Map<String, PersistentProperty> propertiesByName = new HashMap<String,PersistentProperty>();
    protected MappingContext context;
    protected PersistentProperty identity;
    protected PersistentProperty version;
    protected List<String> persistentPropertyNames;
    private String decapitalizedName;
    protected Set owners;
    private PersistentEntity parentEntity;
    private boolean external;
    private MappingProperties mappingProperties = new MappingProperties();

    public AbstractPersistentEntity(Class javaClass, MappingContext context) {
        Assert.notNull(javaClass, "The argument [javaClass] cannot be null");
        this.javaClass = javaClass;
        this.context = context;
        decapitalizedName = Introspector.decapitalize(javaClass.getSimpleName());
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public MappingContext getMappingContext() {
        return context;
    }

    public void initialize() {
        identity = context.getMappingSyntaxStrategy().getIdentity(javaClass, context);
        owners = context.getMappingSyntaxStrategy().getOwningEntities(javaClass, context);
        persistentProperties = context.getMappingSyntaxStrategy().getPersistentProperties(javaClass, context);
        persistentPropertyNames = new ArrayList<String>();
        associations = new ArrayList();

        for (PersistentProperty persistentProperty : persistentProperties) {

            if (!(persistentProperty instanceof OneToMany)) {
                persistentPropertyNames.add(persistentProperty.getName());
            }

            if (persistentProperty instanceof Association) {
                associations.add((Association) persistentProperty);
            }

            propertiesByName.put(persistentProperty.getName(), persistentProperty);
        }

        Class superClass = javaClass.getSuperclass();
        if (superClass != null &&
                !superClass.equals(Object.class) &&
                !Modifier.isAbstract(superClass.getModifiers())) {
            parentEntity = context.addPersistentEntity(superClass);
        }

        getMapping().getMappedForm(); // initialize mapping

        initializeMappingProperties();
        if (mappingProperties.isVersioned()) {
            version = propertiesByName.get("version");
        }
    }

    public boolean hasProperty(String name, Class type) {
        final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(getJavaClass(), name);
        return pd != null && pd.getPropertyType().equals(type);
    }

    public boolean isIdentityName(String propertyName) {
        return getIdentity().getName().equals(propertyName);
    }

    public PersistentEntity getParentEntity() {
        return parentEntity;
    }

    public String getDiscriminator() {
        return getJavaClass().getSimpleName();
    }

    public PersistentEntity getRootEntity() {
        PersistentEntity root = this;
        PersistentEntity parent = getParentEntity();
        while (parent != null) {
            root = parent;
            parent = parent.getParentEntity();
        }
        return root;
    }

    public boolean isRoot() {
        return getParentEntity() == null;
    }

    public boolean isOwningEntity(PersistentEntity owner) {
        return owner != null && owners.contains(owner.getJavaClass());
    }

    public String getDecapitalizedName() {
        return decapitalizedName;
    }

    public List<String> getPersistentPropertyNames() {
        return persistentPropertyNames;
    }

    public ClassMapping<T> getMapping() {
        return new AbstractClassMapping<T>(this, context) {
            @Override
            public T getMappedForm() {
                return null;
            }
        };
    }

    public Object newInstance() {
        try {
            return getJavaClass().newInstance();
        } catch (InstantiationException e) {
            throw new EntityCreationException("Unable to create entity of type [" + getJavaClass().getName() +
                    "]: " + e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new EntityCreationException("Unable to create entity of type [" + getJavaClass().getName() +
                    "]: " + e.getMessage(), e);
        }
    }

    public String getName() {
        return javaClass.getName();
    }

    public PersistentProperty getIdentity() {
        return identity;
    }

    public PersistentProperty getVersion() {
        return version;
    }

    public boolean isVersioned() {
        return version != null && mappingProperties.isVersioned();
    }

    public Class getJavaClass() {
        return javaClass;
    }

    public boolean isInstance(Object obj) {
        return getJavaClass().isInstance(obj);
    }

    public List<PersistentProperty> getPersistentProperties() {
        return persistentProperties;
    }

    public List<Association> getAssociations() {
        return associations;
    }

    public PersistentProperty getPropertyByName(String name) {
        return propertiesByName.get(name);
    }

    private void initializeMappingProperties() {
        MappingConfigurationBuilder builder = new MappingConfigurationBuilder(
              mappingProperties, MappingProperties.class);

        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(getJavaClass());
        Closure value = cpf.getStaticPropertyValue(GormProperties.MAPPING, Closure.class);
        if (value != null) {
            builder.evaluate(value);
        }

        Object mappingVersion = builder.getProperties().get(MappingConfigurationBuilder.VERSION_KEY);
        if (mappingVersion instanceof Boolean) {
            mappingProperties.setVersion((Boolean)mappingVersion);
        }
    }

    private static class MappingProperties {
        private Boolean version;

        public void setVersion(final boolean version) {
            this.version = version;
        }

        public boolean isVersioned() {
            return version == null ? true : version;
        }
    }

    @Override
    public int hashCode() {
        return javaClass.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof PersistentEntity)) return false;
        if (this == o) return true;

        PersistentEntity other = (PersistentEntity) o;
        return javaClass.equals(other.getJavaClass());
    }

    @Override
    public String toString() {
        return javaClass.getName();
    }
}
