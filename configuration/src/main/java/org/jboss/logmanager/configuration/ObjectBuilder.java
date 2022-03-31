/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.configuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.jboss.logmanager.LogContext;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

/**
 * Helper to lazily build an object.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"UnusedReturnValue"})
class ObjectBuilder<T> {

    private final LogContext logContext;
    private final ContextConfiguration contextConfiguration;
    private final Class<? extends T> baseClass;
    private final String className;
    private final Map<String, String> constructorProperties;
    private final Map<String, String> properties;
    private final Set<PropertyValue> definedProperties;
    private final Set<String> postConstructMethods;
    private String moduleName;

    private ObjectBuilder(final LogContext logContext, final ContextConfiguration contextConfiguration,
                          final Class<? extends T> baseClass, final String className) {
        this.logContext = logContext;
        this.contextConfiguration = contextConfiguration;
        this.baseClass = baseClass;
        this.className = className;
        constructorProperties = new LinkedHashMap<>();
        properties = new LinkedHashMap<>();
        definedProperties = new LinkedHashSet<>();
        postConstructMethods = new LinkedHashSet<>();
    }

    /**
     * Create a new {@link ObjectBuilder}.
     *
     * @param logContext the log context being configured
     * @param baseClass  the base type
     * @param className  the name of the class to create
     * @param <T>        the type being created
     *
     * @return a new {@link ObjectBuilder}
     */
    static <T> ObjectBuilder<T> of(final LogContext logContext, final ContextConfiguration contextConfiguration,
                                   final Class<? extends T> baseClass, final String className) {
        return new ObjectBuilder<>(logContext, contextConfiguration, baseClass, className);
    }

    /**
     * Adds a property used for constructing the object.
     * <p>
     * The {@code name} must be the base name for a getter or setter so the type can be determined.
     * </p>
     *
     * @param name  the name of the property
     * @param value a string representing the value
     *
     * @return this builder
     */
    ObjectBuilder<T> addConstructorProperty(final String name, final String value) {
        constructorProperties.put(name, value);
        return this;
    }

    /**
     * Adds a method name to be executed after the object is created and the properties are set. The method must not
     * have any parameters.
     *
     * @param methodNames the name of the method to execute
     *
     * @return this builder
     */
    ObjectBuilder<T> addPostConstructMethods(final String... methodNames) {
        if (methodNames != null) {
            Collections.addAll(postConstructMethods, methodNames);
        }
        return this;
    }

    /**
     * Adds a property to be set on the object after it has been created.
     *
     * @param name  the name of the property
     * @param value a string representing the value
     *
     * @return this builder
     */
    ObjectBuilder<T> addProperty(final String name, final String value) {
        properties.put(name, value);
        return this;
    }

    /**
     * Adds a defined property to be set after the object is created.
     *
     * @param name  the name of the property
     * @param type  the type of the property
     * @param value a supplier for the property value
     *
     * @return this builder
     */
    ObjectBuilder<T> addDefinedProperty(final String name, final Class<?> type, final Supplier<?> value) {
        definedProperties.add(new PropertyValue(name, type, value));
        return this;
    }

    /**
     * Sets the name of the module used to load the object being created.
     *
     * @param moduleName the module name or {@code null} to use this class loader
     *
     * @return this builder
     */
    ObjectBuilder<T> setModuleName(final String moduleName) {
        this.moduleName = moduleName;
        return this;
    }

    /**
     * Creates a the object when the {@linkplain Supplier#get() supplier} value is accessed.
     *
     * @return a supplier which can create the object
     */
    Supplier<T> build() {
        if (className == null) {
            throw new IllegalArgumentException("className is null");
        }
        final Map<String, String> constructorProperties = new LinkedHashMap<>(this.constructorProperties);
        final Map<String, String> properties = new LinkedHashMap<>(this.properties);
        final Set<String> postConstructMethods = new LinkedHashSet<>(this.postConstructMethods);
        final String moduleName = this.moduleName;
        return () -> {
            final ClassLoader classLoader;
            if (moduleName != null) {
                try {
                    classLoader = ModuleFinder.getClassLoader(moduleName);
                } catch (Throwable e) {
                    throw new IllegalArgumentException(String.format("Failed to load module \"%s\"", moduleName), e);
                }
            } else {
                classLoader = getClass().getClassLoader();
            }
            final Class<? extends T> actualClass;
            try {
                actualClass = Class.forName(className, true, classLoader).asSubclass(baseClass);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Failed to load class \"%s\"", className), e);
            }
            final int length = constructorProperties.size();
            final Class<?>[] paramTypes = new Class<?>[length];
            final Object[] params = new Object[length];
            int i = 0;
            for (Map.Entry<String, String> entry : constructorProperties.entrySet()) {
                final String property = entry.getKey();
                final Class<?> type = getConstructorPropertyType(actualClass, property);
                if (type == null) {
                    throw new IllegalArgumentException(String.format("No property named \"%s\" in \"%s\"", property, className));
                }
                paramTypes[i] = type;
                params[i] = getValue(actualClass, property, type, entry.getValue());
                i++;
            }
            final Constructor<? extends T> constructor;
            try {
                constructor = actualClass.getConstructor(paramTypes);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Failed to locate constructor in class \"%s\"", className), e);
            }

            // Get all the setters
            final Map<Method, Object> setters = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                final Method method = getPropertySetter(actualClass, entry.getKey());
                if (method == null) {
                    throw new IllegalArgumentException(String.format("Failed to locate setter for property \"%s\" on type \"%s\"", entry.getKey(), className));
                }
                // Get the value type for the setter
                Class<?> type = getPropertyType(method);
                if (type == null) {
                    throw new IllegalArgumentException(String.format("Failed to determine type for setter \"%s\" on type \"%s\"", method.getName(), className));
                }
                setters.put(method, getValue(actualClass, entry.getKey(), type, entry.getValue()));
            }

            // Define known type parameters
            for (PropertyValue value : definedProperties) {
                final String methodName = getPropertySetterName(value.name);
                try {
                    final Method method = actualClass.getMethod(methodName, value.type);
                    setters.put(method, value.value.get());
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(String.format("Failed to find setter method for property \"%s\" on type \"%s\"", value.name, className), e);
                }
            }

            // Get all the post construct methods
            final Set<Method> postConstruct = new LinkedHashSet<>();
            for (String methodName : postConstructMethods) {
                try {
                    postConstruct.add(actualClass.getMethod(methodName));
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(String.format("Failed to find post construct method \"%s\" on type \"%s\"", methodName, className), e);
                }
            }
            try {
                T instance = constructor.newInstance(params);

                // Execute setters
                for (Map.Entry<Method, Object> entry : setters.entrySet()) {
                    entry.getKey().invoke(instance, entry.getValue());
                }

                // Execute post construct methods
                for (Method method : postConstruct) {
                    method.invoke(instance);
                }

                return instance;
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Failed to instantiate class \"%s\"", className), e);
            }
        };
    }

    private Object getValue(final Class<?> objClass, final String propertyName, final Class<?> paramType, final String value) {
        if (value == null) {
            if (paramType.isPrimitive()) {
                throw new IllegalArgumentException(String.format("Cannot assign null value to primitive property \"%s\" of %s", propertyName, objClass));
            }
            return null;
        }
        if (paramType == String.class) {
            // Don't use the trimmed value for strings
            return value;
        } else if (paramType == java.util.logging.Level.class) {
            return logContext.getLevelForName(value);
        } else if (paramType == java.util.logging.Logger.class) {
            return logContext.getLogger(value);
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return Boolean.valueOf(value);
        } else if (paramType == byte.class || paramType == Byte.class) {
            return Byte.valueOf(value);
        } else if (paramType == short.class || paramType == Short.class) {
            return Short.valueOf(value);
        } else if (paramType == int.class || paramType == Integer.class) {
            return Integer.valueOf(value);
        } else if (paramType == long.class || paramType == Long.class) {
            return Long.valueOf(value);
        } else if (paramType == float.class || paramType == Float.class) {
            return Float.valueOf(value);
        } else if (paramType == double.class || paramType == Double.class) {
            return Double.valueOf(value);
        } else if (paramType == char.class || paramType == Character.class) {
            return value.length() > 0 ? value.charAt(0) : 0;
        } else if (paramType == TimeZone.class) {
            return TimeZone.getTimeZone(value);
        } else if (paramType == Charset.class) {
            return Charset.forName(value);
        } else if (paramType.isAssignableFrom(Level.class)) {
            return Level.parse(value);
        } else if (paramType.isEnum()) {
            return Enum.valueOf(paramType.asSubclass(Enum.class), value);
        } else if (contextConfiguration.hasObject(value)) {
            return contextConfiguration.getObject(value);
        } else if (definedPropertiesContains(propertyName)) {
            final PropertyValue propertyValue = findDefinedProperty(propertyName);
            if (propertyValue == null) {
                throw new IllegalArgumentException("Unknown parameter type for property " + propertyName + " on " + objClass);
            }
            return propertyValue.value.get();
        } else {
            throw new IllegalArgumentException("Unknown parameter type for property " + propertyName + " on " + objClass);
        }
    }

    private boolean definedPropertiesContains(final String name) {
        return findDefinedProperty(name) != null;
    }

    private PropertyValue findDefinedProperty(final String name) {
        for (PropertyValue value : definedProperties) {
            if (name.equals(value.name)) {
                return value;
            }
        }
        return null;
    }

    private static Class<?> getPropertyType(Class<?> clazz, String propertyName) {
        return getPropertyType(getPropertySetter(clazz, propertyName));
    }

    private static Class<?> getPropertyType(final Method setter) {
        return setter != null ? setter.getParameterTypes()[0] : null;
    }

    private static Class<?> getConstructorPropertyType(Class<?> clazz, String propertyName) {
        final Method getter = getPropertyGetter(clazz, propertyName);
        return getter != null ? getter.getReturnType() : getPropertyType(clazz, propertyName);
    }

    private static Method getPropertySetter(Class<?> clazz, String propertyName) {
        final String set = getPropertySetterName(propertyName);
        for (Method method : clazz.getMethods()) {
            if ((method.getName().equals(set) && Modifier.isPublic(method.getModifiers())) && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        return null;
    }

    private static Method getPropertyGetter(Class<?> clazz, String propertyName) {
        final String upperPropertyName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        final Pattern pattern = Pattern.compile("(get|has|is)(" + Pattern.quote(upperPropertyName) + ")");
        for (Method method : clazz.getMethods()) {
            if ((pattern.matcher(method.getName()).matches() && Modifier.isPublic(method.getModifiers())) && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        return null;
    }

    private static String getPropertySetterName(final String propertyName) {
        return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    static class ModuleFinder {

        private ModuleFinder() {
        }

        static ClassLoader getClassLoader(final String moduleName) throws Exception {
            ModuleLoader moduleLoader = ModuleLoader.forClass(ModuleFinder.class);
            if (moduleLoader == null) {
                moduleLoader = Module.getBootModuleLoader();
            }
            return moduleLoader.loadModule(moduleName).getClassLoader();
        }
    }

    private static class PropertyValue implements Comparable<PropertyValue> {
        final String name;
        final Class<?> type;
        final Supplier<?> value;

        private PropertyValue(final String name, final Class<?> type, final Supplier<?> value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }

        @Override
        public int compareTo(final PropertyValue o) {
            return name.compareTo(o.name);
        }
    }
}
