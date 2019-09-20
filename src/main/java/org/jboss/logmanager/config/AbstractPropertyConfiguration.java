/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.config;

import static java.util.Arrays.asList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jboss.logmanager.ModuleFinderFactory;
import org.jboss.logmanager.StandardOutputStreams;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractPropertyConfiguration<T, C extends AbstractPropertyConfiguration<T, C>> extends AbstractBasicConfiguration<T, C> implements ObjectConfigurable<T>, PropertyConfigurable {
    private final Class<? extends T> actualClass;
    private final String moduleName;
    private final String className;
    private final String[] constructorProperties;
    private final Map<String, ValueExpression<String>> properties = new LinkedHashMap<String, ValueExpression<String>>(0);
    private final Map<String, Method> postConfigurationMethods = new LinkedHashMap<String, Method>();

    protected AbstractPropertyConfiguration(final Class<T> baseClass, final LogContextConfigurationImpl configuration, final Map<String, T> refs, final Map<String, C> configs, final String name, final String moduleName, final String className, final String[] constructorProperties) {
        super(name, configuration, refs, configs);
        this.moduleName = moduleName;
        this.className = className;
        if (className == null) {
            throw new IllegalArgumentException("className is null");
        }
        this.constructorProperties = constructorProperties;
        final ClassLoader classLoader;
        if (moduleName != null) try {
            classLoader = ModuleFinderFactory.getInstance().resolve(moduleName);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format("Failed to load module \"%s\" for %s \"%s\"", moduleName, getDescription(), name), e);
        }
        else {
            classLoader = getClass().getClassLoader();
        }
        final Class<? extends T> actualClass;
        try {
            actualClass = Class.forName(className, true, classLoader).asSubclass(baseClass);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Failed to load class \"%s\" for %s \"%s\"", className, getDescription(), name), e);
        }
        this.actualClass = actualClass;
    }

    ConfigAction<T> getConstructAction() {
        return new ConstructAction();
    }

    abstract String getDescription();

    class ConstructAction implements ConfigAction<T> {

        public T validate() throws IllegalArgumentException {
            final int length = constructorProperties.length;
            final Class<?>[] paramTypes = new Class<?>[length];
            for (int i = 0; i < length; i++) {
                final String property = constructorProperties[i];
                final Class<?> type = getConstructorPropertyType(actualClass, property);
                if (type == null) {
                    throw new IllegalArgumentException(String.format("No property named \"%s\" for %s \"%s\"", property, getDescription(), getName()));
                }
                paramTypes[i] = type;
            }
            final Constructor<? extends T> constructor;
            try {
                constructor = actualClass.getConstructor(paramTypes);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Failed to locate constructor in class \"%s\" for %s \"%s\"", className, getDescription(), getName()), e);
            }
            final Object[] params = new Object[length];
            for (int i = 0; i < length; i++) {
                final String property = constructorProperties[i];
                if (! properties.containsKey(property)) {
                    throw new IllegalArgumentException(String.format("No property named \"%s\" is configured on %s \"%s\"", property, getDescription(), getName()));
                }
                final ValueExpression valueExpression = properties.get(property);
                final Object value = getConfiguration().getValue(actualClass, property, paramTypes[i], valueExpression, true).getObject();
                params[i] = value;
            }
            try {
                return constructor.newInstance(params);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Failed to instantiate class \"%s\" for %s \"%s\"", className, getDescription(), getName()), e);
            }
        }

        public void applyPreCreate(final T param) {
            getRefs().put(getName(), param);
        }

        public void applyPostCreate(T param) {
        }

        public void rollback() {
            getConfigs().remove(getName());
        }
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getClassName() {
        return className;
    }

    static boolean contains(Object[] array, Object val) {
        for (Object o : array) {
            if (o.equals(val)) return true;
        }
        return false;
    }

    public void setPropertyValueString(final String propertyName, final String value) throws IllegalArgumentException {
        if (isRemoved()) {
            throw new IllegalArgumentException(String.format("Cannot set property \"%s\" on %s \"%s\" (removed)", propertyName, getDescription(), getName()));
        }
        if (propertyName == null) {
            throw new IllegalArgumentException("propertyName is null");
        }
        setPropertyValueExpression(propertyName, ValueExpression.STRING_RESOLVER.resolve(value));
    }

    public String getPropertyValueString(final String propertyName) {
        return getPropertyValueExpression(propertyName).getResolvedValue();
    }

    @Override
    public ValueExpression<String> getPropertyValueExpression(final String propertyName) {
        return properties.containsKey(propertyName) ? properties.get(propertyName) : ValueExpression.NULL_STRING_EXPRESSION;
    }

    @Override
    public void setPropertyValueExpression(final String propertyName, final String expression) {
        if (isRemoved()) {
            throw new IllegalArgumentException(String.format("Cannot set property \"%s\" on %s \"%s\" (removed)", propertyName, getDescription(), getName()));
        }
        if (propertyName == null) {
            throw new IllegalArgumentException("propertyName is null");
        }
        setPropertyValueExpression(propertyName, ValueExpression.STRING_RESOLVER.resolve(expression));
    }

    @Override
    public void setPropertyValueExpression(final String propertyName, final String expression, final String value) {
        if (isRemoved()) {
            throw new IllegalArgumentException(String.format("Cannot set property \"%s\" on %s \"%s\" (removed)", propertyName, getDescription(), getName()));
        }
        if (propertyName == null) {
            throw new IllegalArgumentException("propertyName is null");
        }
        setPropertyValueExpression(propertyName, new ValueExpressionImpl<String>(expression, value));
    }

    private void setPropertyValueExpression(final String propertyName, final ValueExpression<String> expression) {
        final boolean replacement = properties.containsKey(propertyName);
        final boolean constructorProp = contains(constructorProperties, propertyName);
        final Method setter = getPropertySetter(actualClass, propertyName);
        if (setter == null && ! constructorProp) {
            throw new IllegalArgumentException(String.format("No property \"%s\" setter found for %s \"%s\"", propertyName, getDescription(), getName()));
        }
        final ValueExpression<String> oldValue = properties.put(propertyName, expression);
        getConfiguration().addAction(new ConfigAction<ObjectProducer>() {
            public ObjectProducer validate() throws IllegalArgumentException {
                if (setter == null) {
                    return ObjectProducer.NULL_PRODUCER;
                }
                final Class<?> propertyType = getPropertyType(actualClass, propertyName);
                if (propertyType == null) {
                    throw new IllegalArgumentException(String.format("No property \"%s\" type could be determined for %s \"%s\"", propertyName, getDescription(), getName()));
                }
                return getConfiguration().getValue(actualClass, propertyName, propertyType, expression, false);
            }

            public void applyPreCreate(final ObjectProducer param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final ObjectProducer param) {
                if (setter != null) {
                    final T instance = getRefs().get(getName());
                    try {
                        setter.invoke(instance, param.getObject());
                    } catch (Throwable e) {
                        StandardOutputStreams.printError(e, "Failed to invoke setter %s with value %s%n.", setter.getName(), param.getObject());
                    }
                }
            }

            public void rollback() {
                final Class<?> propertyType = getPropertyType(actualClass, propertyName);
                if (propertyType == null) {
                    // We don't want the rest of the rollback to fail so we'll just log a message
                    StandardOutputStreams.printError("No property \"%s\" type could be determined for %s \"%s\"", propertyName, getDescription(), getName());
                    return;
                }
                final ObjectProducer producer;
                if (replacement) {
                    properties.put(propertyName, oldValue);
                    producer = getConfiguration().getValue(actualClass, propertyName, propertyType, oldValue, true);
                } else {
                    properties.remove(propertyName);
                    producer = getDefaultValue(propertyType);
                }
                if (setter != null) {
                    // Get the reference instance, the old value and reset to the old value
                    final T instance = getRefs().get(getName());
                    if (instance != null) {
                        try {
                            setter.invoke(instance, producer.getObject());
                        } catch (Throwable e) {
                            StandardOutputStreams.printError(e, "Failed to invoke setter %s with value %s%n.", setter.getName(), producer.getObject());
                        }
                    } else {
                        // If the instance is not available don't keep the property
                        properties.remove(propertyName);
                    }
                }
            }
        });
    }

    public boolean hasProperty(final String propertyName) {
        return properties.containsKey(propertyName);
    }

    public boolean removeProperty(final String propertyName) {
        if (isRemoved()) {
            throw new IllegalArgumentException(String.format("Cannot remove property \"%s\" on %s \"%s\" (removed)", propertyName, getDescription(), getName()));
        }
        final Method setter = getPropertySetter(actualClass, propertyName);
        if (setter == null) {
            throw new IllegalArgumentException(String.format("No property \"%s\" setter found for %s \"%s\"", propertyName, getDescription(), getName()));
        }
        final ValueExpression<String> oldValue = properties.remove(propertyName);
        if (oldValue != null) {
            getConfiguration().addAction(new ConfigAction<ObjectProducer>() {
                public ObjectProducer validate() throws IllegalArgumentException {
                    final Class<?> propertyType = getPropertyType(actualClass, propertyName);
                    if (propertyType == null) {
                        throw new IllegalArgumentException(String.format("No property \"%s\" type could be determined for %s \"%s\"", propertyName, getDescription(), getName()));
                    }
                    return getDefaultValue(propertyType);
                }

                public void applyPreCreate(final ObjectProducer param) {
                    addPostConfigurationActions();
                }

                public void applyPostCreate(final ObjectProducer param) {
                    final T instance = getRefs().get(getName());
                    try {
                        setter.invoke(instance, param.getObject());
                    } catch (Throwable e) {
                        StandardOutputStreams.printError(e, "Failed to invoke setter %s with value %s%n.", setter.getName(), param.getObject());
                    }
                }

                public void rollback() {
                    // We need to once again determine the property type
                    final Class<?> propertyType = getPropertyType(actualClass, propertyName);
                    if (propertyType == null) {
                        // We don't want the rest of the rollback to fail so we'll just log a message
                        StandardOutputStreams.printError("No property \"%s\" type could be determined for %s \"%s\"", propertyName, getDescription(), getName());
                        return;
                    }
                    // Get the reference instance, the old value and reset to the old value
                    final T instance = getRefs().get(getName());
                    final ObjectProducer producer = getConfiguration().getValue(actualClass, propertyName, propertyType, oldValue, true);
                    try {
                        setter.invoke(instance, producer.getObject());
                    } catch (Throwable e) {
                        StandardOutputStreams.printError(e, "Failed to invoke setter %s with value %s%n.", setter.getName(), producer.getObject());
                    }
                }
            });
            return true;
        }
        return false;
    }

    public List<String> getPropertyNames() {
        return new ArrayList<String>(properties.keySet());
    }

    @Override
    public boolean hasConstructorProperty(final String propertyName) {
        return contains(constructorProperties, propertyName);
    }

    Class<? extends T> getActualClass() {
        return actualClass;
    }

    @Override
    public List<String> getConstructorProperties() {
        return Arrays.asList(constructorProperties);
    }

    @Override
    public boolean addPostConfigurationMethod(final String methodName) {
        final LogContextConfigurationImpl configuration = getConfiguration();
        if (postConfigurationMethods.containsKey(methodName)) {
            return false;
        }
        configuration.addAction(new ConfigAction<Method>() {
            public Method validate() throws IllegalArgumentException {
                try {
                    return actualClass.getMethod(methodName);
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(String.format("Method '%s' not found on '%s'", methodName, actualClass.getName()));
                }
            }

            public void applyPreCreate(final Method param) {
            }

            public void applyPostCreate(final Method param) {
                postConfigurationMethods.put(methodName, param);
                // TODO (jrp) this isn't the best for performance
                addPostConfigurationActions(true);
            }

            public void rollback() {
                postConfigurationMethods.remove(methodName);
                addPostConfigurationActions(true);
            }
        });
        return true;
    }

    @Override
    public List<String> getPostConfigurationMethods() {
        return new ArrayList<String>(postConfigurationMethods.keySet());
    }

    @Override
    public void setPostConfigurationMethods(final String... methodNames) {
        setPostConfigurationMethods(asList(methodNames));
    }

    @Override
    public void setPostConfigurationMethods(final List<String> methodNames) {
        final Map<String, Method> oldMethods = new LinkedHashMap<String, Method>(postConfigurationMethods);
        postConfigurationMethods.clear();
        final LinkedHashSet<String> names = new LinkedHashSet<String>(methodNames);
        getConfiguration().addAction(new ConfigAction<Map<String, Method>>() {
            @Override
            public Map<String, Method> validate() throws IllegalArgumentException {
                final Map<String, Method> result = new LinkedHashMap<String, Method>();
                for (String methodName : names) {
                    try {
                        result.put(methodName, actualClass.getMethod(methodName));
                    } catch (NoSuchMethodException e) {
                        throw new IllegalArgumentException(String.format("Method '%s' not found on '%s'", methodName, actualClass.getName()));
                    }

                }
                return result;
            }

            @Override
            public void applyPreCreate(final Map<String, Method> param) {
            }

            @Override
            public void applyPostCreate(final Map<String, Method> param) {
                postConfigurationMethods.clear();
                postConfigurationMethods.putAll(param);
                addPostConfigurationActions(true);
            }

            @Override
            public void rollback() {
                postConfigurationMethods.clear();
                postConfigurationMethods.putAll(oldMethods);
                addPostConfigurationActions(true);
            }
        });
    }

    @Override
    public boolean removePostConfigurationMethod(final String methodName) {
        final LogContextConfigurationImpl configuration = getConfiguration();
        if (!postConfigurationMethods.containsKey(methodName)) {
            return false;
        }
        final Method method = postConfigurationMethods.get(methodName);
        postConfigurationMethods.remove(methodName);
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
            }

            public void rollback() {
                postConfigurationMethods.put(methodName, method);
                addPostConfigurationActions(true);
            }
        });
        return true;
    }

    @Override
    public T getInstance() {
        return refs.get(getName());
    }

    protected final void addPostConfigurationActions() {
        addPostConfigurationActions(false);
    }

    private void addPostConfigurationActions(final boolean replace) {
        final String name = className + "." + getName();
        final LogContextConfigurationImpl configuration = getConfiguration();
        if (!replace && configuration.postConfigurationActionsExist(name)) {
            return;
        }
        final Deque<ConfigAction<?>> queue = new ArrayDeque<ConfigAction<?>>(postConfigurationMethods.size());
        for (final String methodName : postConfigurationMethods.keySet()) {
            final ConfigAction<Method> configAction = new ConfigAction<Method>() {

                public Method validate() throws IllegalArgumentException {
                    final Method result = postConfigurationMethods.get(methodName);
                    if (result == null) {
                        throw new IllegalArgumentException(String.format("Method '%s' not found on '%s'", methodName, actualClass.getName()));
                    }
                    return result;
                }

                public void applyPreCreate(final Method param) {
                }

                public void applyPostCreate(final Method param) {
                    final T instance = getRefs().get(getName());
                    try {
                        param.invoke(instance);
                    } catch (Throwable e) {
                        StandardOutputStreams.printError(e, "Failed to invoke method %s%n.", param.getName());
                    }
                }

                public void rollback() {
                    // ignore any rollbacks at this point
                }
            };
            queue.addLast(configAction);
        }
        configuration.addPostConfigurationActions(name, queue);
    }

    protected final Deque<?> removePostConfigurationActions() {
        final String name = className + "." + getName();
        return getConfiguration().removePostConfigurationActions(name);
    }

    static Class<?> getPropertyType(Class<?> clazz, String propertyName) {
        final Method setter = getPropertySetter(clazz, propertyName);
        return setter != null ? setter.getParameterTypes()[0] : null;
    }

    static Class<?> getConstructorPropertyType(Class<?> clazz, String propertyName) {
        final Method getter = getPropertyGetter(clazz, propertyName);
        return getter != null ? getter.getReturnType() : getPropertyType(clazz, propertyName);
    }

    static Method getPropertySetter(Class<?> clazz, String propertyName) {
        final String upperPropertyName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        final String set = "set" + upperPropertyName;
        for (Method method : clazz.getMethods()) {
            if ((method.getName().equals(set) && Modifier.isPublic(method.getModifiers())) && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        return null;
    }

    static Method getPropertyGetter(Class<?> clazz, String propertyName) {
        final String upperPropertyName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        final Pattern pattern = Pattern.compile("(get|has|is)(" + Pattern.quote(upperPropertyName) + ")");
        for (Method method : clazz.getMethods()) {
            if ((pattern.matcher(method.getName()).matches() && Modifier.isPublic(method.getModifiers())) && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        return null;
    }

    private static ObjectProducer getDefaultValue(final Class<?> paramType) {
        if (paramType == boolean.class) {
            return new SimpleObjectProducer(Boolean.FALSE);
        } else if (paramType == byte.class) {
            return new SimpleObjectProducer((byte) 0x00);
        } else if (paramType == short.class) {
            return new SimpleObjectProducer((short) 0);
        } else if (paramType == int.class) {
            return new SimpleObjectProducer(0);
        } else if (paramType == long.class) {
            return new SimpleObjectProducer(0L);
        } else if (paramType == float.class) {
            return new SimpleObjectProducer(0.0f);
        } else if (paramType == double.class) {
            return new SimpleObjectProducer(0.0d);
        } else if (paramType == char.class) {
            return new SimpleObjectProducer((char) 0x00);
        } else {
            return SimpleObjectProducer.NULL_PRODUCER;
        }
    }
}
