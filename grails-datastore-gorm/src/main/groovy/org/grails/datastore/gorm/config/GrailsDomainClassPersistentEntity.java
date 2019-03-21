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

package org.grails.datastore.gorm.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.PropertyMapping;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.datastore.mapping.model.types.ManyToOne;
import org.grails.datastore.mapping.model.types.OneToMany;
import org.grails.datastore.mapping.model.types.OneToOne;

/**
 * Bridges the {@link GrailsDomainClass} interface into the {@link PersistentEntity} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("hiding")
public class GrailsDomainClassPersistentEntity implements PersistentEntity {

    private GrailsDomainClass domainClass;
    private GrailsDomainClassMappingContext mappingContext;
    private GrailsDomainClassPersistentProperty identifier;
    private GrailsDomainClassPersistentProperty version;
    private Map<String, PersistentProperty> propertiesByName = new HashMap<String, PersistentProperty>();
    private List<PersistentProperty> properties = new ArrayList<PersistentProperty>();
    private List<Association> associations = new ArrayList<Association>();

    public GrailsDomainClassPersistentEntity(GrailsDomainClass domainClass,
            GrailsDomainClassMappingContext mappingContext) {
        this.domainClass = domainClass;
        this.mappingContext = mappingContext;
    }

    /**
     * @return The wrapped GrailsDomainClass instance
     */
    public GrailsDomainClass getDomainClass() {
        return domainClass;
    }

    public void initialize() {
        identifier = new GrailsDomainClassPersistentProperty(this, domainClass.getIdentifier());
        version = new GrailsDomainClassPersistentProperty(this, domainClass.getVersion());

        mappingContext.addEntityValidator(this, domainClass.getValidator());

        final GrailsDomainClassProperty[] persistentProperties = domainClass.getPersistentProperties();
        for (GrailsDomainClassProperty grailsDomainClassProperty : persistentProperties) {
            PersistentProperty persistentProperty;
            if (grailsDomainClassProperty.isAssociation()) {
                if (grailsDomainClassProperty.isEmbedded()) {
                    persistentProperty = createEmbedded(mappingContext,grailsDomainClassProperty);
                }
                else if (grailsDomainClassProperty.isOneToMany()) {
                    persistentProperty = createOneToMany(mappingContext, grailsDomainClassProperty);
                }
                else if (grailsDomainClassProperty.isHasOne()) {
                    persistentProperty = createOneToOne(mappingContext, grailsDomainClassProperty);
                }
                else if (grailsDomainClassProperty.isOneToOne()) {
                    persistentProperty = createOneToOne(mappingContext, grailsDomainClassProperty);
                }
                else if (grailsDomainClassProperty.isManyToOne()) {
                    persistentProperty = createManyToOne(mappingContext, grailsDomainClassProperty);
                }
                else if (grailsDomainClassProperty.isManyToMany()) {
                    persistentProperty = createManyToMany(mappingContext, grailsDomainClassProperty);
                }
                else {
                    persistentProperty = new GrailsDomainClassPersistentProperty(this, grailsDomainClassProperty);
                }
            }
            else {
                persistentProperty = new GrailsDomainClassPersistentProperty(this, grailsDomainClassProperty);
            }
            propertiesByName.put(grailsDomainClassProperty.getName(), persistentProperty);
            properties.add(persistentProperty);
        }
    }

    public String getName() {
        return domainClass.getFullName();
    }

    public PersistentProperty getIdentity() {
        return identifier;
    }

    public PersistentProperty getVersion() {
        return version;
    }

    public boolean isVersioned() {
        // TODO
        return version != null;
    }

    public List<PersistentProperty> getPersistentProperties() {
        return properties;
    }

    public List<Association> getAssociations() {
        return associations;
    }

    public PersistentProperty getPropertyByName(String name) {
        return propertiesByName.get(name);
    }

    public Class getJavaClass() {
        return domainClass.getClazz();
    }

    public boolean isInstance(Object obj) {
        return domainClass.getClazz().isInstance(obj);
    }

    public ClassMapping getMapping() {
        return null;
    }

    public Object newInstance() {
        return domainClass.newInstance();
    }

    public List<String> getPersistentPropertyNames() {
        return new ArrayList<String>( propertiesByName.keySet() );
    }

    public String getDecapitalizedName() {
        return domainClass.getLogicalPropertyName();
    }

    public boolean isOwningEntity(PersistentEntity owner) {
        return domainClass.isOwningClass(owner.getJavaClass());
    }

    public PersistentEntity getParentEntity() {
        if (!isRoot()) {
            return getMappingContext().getPersistentEntity(
                    getJavaClass().getSuperclass().getName());
        }
        return null;
    }

    public PersistentEntity getRootEntity() {
        if (isRoot() || getParentEntity() == null) {
            return this;
        }
        PersistentEntity parent = getParentEntity();
        while (!parent.isRoot()) {
            parent = parent.getParentEntity();
        }
        return parent;
    }

    public boolean isRoot() {
        return domainClass.isRoot();
    }

    public String getDiscriminator() {
        return getName();
    }

    public MappingContext getMappingContext() {
        return mappingContext;
    }

    public boolean hasProperty(String name, Class type) {
        return domainClass.hasProperty(name);
    }

    public boolean isIdentityName(String propertyName) {
        return domainClass.getIdentifier().getName().equals(propertyName);
    }

    private PersistentProperty createManyToOne(
            GrailsDomainClassMappingContext ctx,
            GrailsDomainClassProperty grailsDomainClassProperty) {
        final ManyToOne oneToOne = new ManyToOne(this, ctx, grailsDomainClassProperty.getName(), grailsDomainClassProperty.getType()) {
            public PropertyMapping getMapping() {
                return null;
            }
        };
        configureAssociation(grailsDomainClassProperty, oneToOne);
        return oneToOne;
    }

    private PersistentProperty createManyToMany(
            GrailsDomainClassMappingContext ctx,
            GrailsDomainClassProperty grailsDomainClassProperty) {
        final ManyToMany manyToMany = new ManyToMany(this, ctx, grailsDomainClassProperty.getName(), grailsDomainClassProperty.getType()) {
            public PropertyMapping getMapping() {
                return null;
            }
        };
        configureAssociation(grailsDomainClassProperty, manyToMany);
        return manyToMany;
    }

    private PersistentProperty createOneToOne(
            GrailsDomainClassMappingContext ctx,
            GrailsDomainClassProperty grailsDomainClassProperty) {
        final OneToOne oneToOne = new OneToOne(this, ctx, grailsDomainClassProperty.getName(), grailsDomainClassProperty.getType()) {
            public PropertyMapping getMapping() {
                return null;
            }
        };
        configureAssociation(grailsDomainClassProperty, oneToOne);
        return oneToOne;
    }

    private OneToMany createOneToMany(GrailsDomainClassMappingContext mappingContext,
            GrailsDomainClassProperty grailsDomainClassProperty) {
        final OneToMany oneToMany = new OneToMany(this, mappingContext, grailsDomainClassProperty.getName(), grailsDomainClassProperty.getType()) {

            public PropertyMapping getMapping() {
                return null;
            }
        };
        configureAssociation(grailsDomainClassProperty, oneToMany);

        return oneToMany;
    }

    private void configureAssociation(
            GrailsDomainClassProperty grailsDomainClassProperty,
            final Association association) {
        association.setAssociatedEntity(getMappingContext().addPersistentEntity(grailsDomainClassProperty.getReferencedPropertyType()));
        association.setOwningSide(grailsDomainClassProperty.isOwningSide());
        association.setReferencedPropertyName(grailsDomainClassProperty.getReferencedPropertyName());
    }

    private PersistentProperty createEmbedded(
            GrailsDomainClassMappingContext mappingContext,
            GrailsDomainClassProperty grailsDomainClassProperty) {
        Embedded persistentProperty = new Embedded(this, mappingContext, grailsDomainClassProperty.getName(), grailsDomainClassProperty.getClass()) {
            public PropertyMapping getMapping() {
                return null;
            }
        };
        persistentProperty.setOwningSide(grailsDomainClassProperty.isOwningSide());
        persistentProperty.setReferencedPropertyName(grailsDomainClassProperty.getReferencedPropertyName());

        return persistentProperty;
    }

    public boolean isExternal() {
        return false;
    }

    public void setExternal(boolean external) {
        // do nothing
    }
}
