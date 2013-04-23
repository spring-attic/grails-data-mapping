/* 
 * Copyright 2004-2008 the original author or authors.
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
package org.codehaus.groovy.grails.orm.hibernate.cfg;

import grails.util.GrailsWebUtil;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.type.AbstractStandardBasicType;
import org.hibernate.type.TypeResolver;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserType;

/**
 * Hibernate Usertype that enum values by their ID.
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class IdentityEnumType implements UserType, ParameterizedType, Serializable {

    private static final long serialVersionUID = -6625622185856547501L;

    private static final Log LOG = LogFactory.getLog(IdentityEnumType.class);

    private static TypeResolver typeResolver = new TypeResolver();
    public static final String ENUM_ID_ACCESSOR = "getId";

    public static final String PARAM_ENUM_CLASS = "enumClass";

    private static final Map<Class<? extends Enum<?>>, BidiEnumMap> ENUM_MAPPINGS = new HashMap<Class<? extends Enum<?>>, BidiEnumMap>();
    private Class<? extends Enum<?>> enumClass;
    private BidiEnumMap bidiMap;
    private AbstractStandardBasicType<?> type;
    private int[] sqlTypes;

    public static BidiEnumMap getBidiEnumMap(Class<? extends Enum<?>> cls) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BidiEnumMap m = ENUM_MAPPINGS.get(cls);
        if (m == null) {
            synchronized (ENUM_MAPPINGS) {
                if (!ENUM_MAPPINGS.containsKey(cls)) {
                    m = new BidiEnumMap(cls);
                    ENUM_MAPPINGS.put(cls, m);
                }
                else {
                    m = ENUM_MAPPINGS.get(cls);
                }
            }
        }
        return m;
    }

    public static boolean isEnabled() {
        Object disableConfigOption = GrailsWebUtil.currentFlatConfiguration().get("grails.orm.enum.id.mapping");
        return disableConfigOption == null || !(Boolean.FALSE.equals(disableConfigOption));
    }

    @SuppressWarnings("unchecked")
    public static boolean supports(@SuppressWarnings("rawtypes") Class enumClass) {
        if (!isEnabled()) return false;
        if (GrailsClassUtils.isJdk5Enum(enumClass)) {
            try {
                Method idAccessor = enumClass.getMethod(ENUM_ID_ACCESSOR);
                int mods = idAccessor.getModifiers();
                if (Modifier.isPublic(mods) && !Modifier.isStatic(mods)) {
                    Class<?> returnType = idAccessor.getReturnType();
                    return returnType != null && typeResolver.basic(returnType.getName()) instanceof AbstractStandardBasicType;
                }
            }
            catch (NoSuchMethodException e) {
                // ignore
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void setParameterValues(Properties properties) {
        try {
            enumClass = (Class<? extends Enum<?>>)Thread.currentThread().getContextClassLoader().loadClass(
                    (String)properties.get(PARAM_ENUM_CLASS));
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Building ID-mapping for Enum Class %s", enumClass.getName()));
            }
            bidiMap = getBidiEnumMap(enumClass);
            type = (AbstractStandardBasicType<?>)typeResolver.basic(bidiMap.keyType.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Mapped Basic Type is %s", type));
            }
            sqlTypes = type.sqlTypes(null);
        }
        catch (Exception e) {
            throw new MappingException("Error mapping Enum Class using IdentifierEnumType", e);
        }
    }

    public int[] sqlTypes() {
        return sqlTypes;
    }

    public Class<?> returnedClass() {
        return enumClass;
    }

    public boolean equals(Object o1, Object o2) throws HibernateException {
        return o1 == o2;
    }

    public int hashCode(Object o) throws HibernateException {
        return o.hashCode();
    }

    public Object nullSafeGet(ResultSet resultSet, String[] names, SessionImplementor session, Object owner) throws SQLException {
        Object id = type.nullSafeGet(resultSet, names[0], null);
        if ((!resultSet.wasNull()) && id != null) {
            return bidiMap.getEnumValue(id);
        }
        return null;
    }

    public void nullSafeSet(PreparedStatement pstmt, Object value, int idx, SessionImplementor session) throws SQLException {
        if (value == null) {
            pstmt.setNull(idx, sqlTypes[0]);
        }
        else {
            type.nullSafeSet(pstmt, bidiMap.getKey(value), idx, null);
        }
    }

    public Object deepCopy(Object o) throws HibernateException {
        return o;
    }

    public boolean isMutable() {
        return false;
    }

    public Serializable disassemble(Object o) throws HibernateException {
        return (Serializable) o;
    }

    public Object assemble(Serializable cached, Object owner) throws HibernateException {
        return cached;
    }

    public Object replace(Object orig, Object target, Object owner) throws HibernateException {
        return orig;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static class BidiEnumMap implements Serializable {

        private static final long serialVersionUID = 3325751131102095834L;
        private final Map enumToKey;
        private final Map keytoEnum;
        private Class keyType;

        private BidiEnumMap(Class<? extends Enum> enumClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Building Bidirectional Enum Map..."));
            }

            EnumMap enumToKey = new EnumMap(enumClass);
            HashMap keytoEnum = new HashMap();

            Method idAccessor = enumClass.getMethod(ENUM_ID_ACCESSOR);

            keyType = idAccessor.getReturnType();

            Method valuesAccessor = enumClass.getMethod("values");
            Object[] values = (Object[]) valuesAccessor.invoke(enumClass);

            for (Object value : values) {
                Object id = idAccessor.invoke(value);
                enumToKey.put((Enum) value, id);
                if (keytoEnum.containsKey(id)) {
                    LOG.warn(String.format("Duplicate Enum ID '%s' detected for Enum %s!", id, enumClass.getName()));
                }
                keytoEnum.put(id, value);
            }

            this.enumToKey = Collections.unmodifiableMap(enumToKey);
            this.keytoEnum = Collections.unmodifiableMap(keytoEnum);
        }

        public Object getEnumValue(Object id) {
            return keytoEnum.get(id);
        }

        public Object getKey(Object enumValue) {
            return enumToKey.get(enumValue);
        }
    }
}
