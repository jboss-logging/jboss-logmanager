/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.logmanager;

import java.io.IOException;
import java.io.InputStream;
import java.io.Closeable;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TimeZone;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Formatter;
import java.util.logging.ErrorManager;

/**
 * A configurator which uses a simple property file format.  Use of this class adds a requirement on the JBoss
 * Common Core project.
 */
public final class PropertyConfigurator implements Configurator {

    private static final String[] EMPTY_STRINGS = new String[0];

    /**
     * Construct an instance.
     */
    public PropertyConfigurator() {
    }

    private Map<String, Handler> configuredHandlers;
    private Map<String, Filter> configuredFilters;
    private Map<String, Formatter> configuredFormatters;
    private Map<String, ErrorManager> configuredErrorManagers;
    private Map<String, Logger> configuredLoggers;

    /** {@inheritDoc} */
    public void configure(final InputStream inputStream) throws IOException {
        configuredHandlers = new HashMap<String, Handler>();
        configuredFilters = new HashMap<String, Filter>();
        configuredFormatters = new HashMap<String, Formatter>();
        configuredErrorManagers = new HashMap<String, ErrorManager>();
        configuredLoggers = new HashMap<String, Logger>();
        final Properties properties = new Properties();
        try {
            properties.load(new InputStreamReader(inputStream, "utf-8"));
            inputStream.close();
        } finally {
            safeClose(inputStream);
        }
        configure(properties);
    }

    private void configure(final Properties properties) throws IOException {
        // Start with the list of loggers to configure.  The root logger is always on the list.
        final List<String> loggerNames = getStringCsvList(properties, "loggers", "");
        final Set<String> done = new HashSet<String>();

        // Now, for each logger name, configure any filters, handlers, etc.
        for (String loggerName : loggerNames) {
            if (! done.add(loggerName)) {
                // duplicate
                continue;
            }
            final Logger logger = LogContext.getSystemLogContext().getLogger(loggerName);
            configuredLoggers.put(loggerName, logger);

            // Get logger level
            final String levelName = getStringProperty(properties, getKey("logger", loggerName, "level"));
            if (levelName != null) {
                logger.setLevel(LogContext.getSystemLogContext().getLevelForName(levelName));
            }

            // Get logger filter
            final String filterName = getStringProperty(properties, getKey("logger", loggerName, "filter"));
            if (filterName != null) {
                logger.setFilter(configureFilter(properties, filterName));
            }

            // Get logger handlers
            final List<String> handlerNames = getStringCsvList(properties, getKey("logger", loggerName, "handlers"));
            for (String handlerName : handlerNames) {
                logger.addHandler(configureHandler(properties, handlerName));
            }

            // Get logger properties
            final String useParentHandlersString = getStringProperty(properties, getKey("logger", loggerName, "useParentHandlers"));
            if (useParentHandlersString != null) {
                logger.setUseParentHandlers(Boolean.parseBoolean(useParentHandlersString));
            }
        }
    }

    private void configureProperties(final Properties properties, final Object object, final String prefix) throws IOException {
        final List<String> propertyNames = getStringCsvList(properties, getKey(prefix, "properties"));
        final Class<? extends Object> objClass = object.getClass();
        final Iterator<String> it = propertyNames.iterator();
        if (! it.hasNext()) {
            return;
        } else {
            final Map<String, Method> setters = new HashMap<String, Method>();
            for (Method method : objClass.getMethods()) {
                final int modifiers = method.getModifiers();
                if (Modifier.isStatic(modifiers) || ! Modifier.isPublic(modifiers)) {
                    continue;
                }
                final String name = method.getName();
                if (! name.startsWith("set")) {
                    continue;
                }
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1) {
                    continue;
                }
                if (method.getReturnType() != void.class) {
                    continue;
                }
                setters.put(name.substring(3, 4).toLowerCase() + name.substring(4), method);
            }
            do {
                String propertyName = it.next();
                final String propValue = getStringProperty(properties, getKey(prefix, propertyName));
                if (propValue != null) {
                    final Object argument;
                    final Method method = setters.get(propertyName);
                    if (method == null) {
                        throw new IllegalArgumentException("Declared property " + propertyName + " wasn't found on " + objClass);
                    }
                    argument = getArgument(properties, method, propertyName, propValue);
                    try {
                        method.invoke(object, argument);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Unable to set property " + propertyName + " on " + objClass, e);
                    }
                }
            } while (it.hasNext());
        }
    }

    private Object getArgument(final Properties properties, final Method method, final String propertyName, final String propValue) throws IOException {
        final Class<? extends Object> objClass = method.getDeclaringClass();
        final Object argument;
        final Class<?> paramType = method.getParameterTypes()[0];
        if (paramType == String.class) {
            argument = propValue;
        } else if (paramType == Handler.class) {
            argument = configureHandler(properties, propValue);
        } else if (paramType == Filter.class) {
            argument = configureFilter(properties, propValue);
        } else if (paramType == Formatter.class) {
            argument = configureFormatter(properties, propValue);
        } else if (paramType == java.util.logging.Level.class) {
            argument = LogContext.getSystemLogContext().getLevelForName(propValue);
        } else if (paramType == java.util.logging.Logger.class) {
            argument = LogContext.getSystemLogContext().getLogger(propValue);
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            argument = Boolean.valueOf(propValue);
        } else if (paramType == byte.class || paramType == Byte.class) {
            argument = Byte.valueOf(propValue);
        } else if (paramType == short.class || paramType == Short.class) {
            argument = Short.valueOf(propValue);
        } else if (paramType == int.class || paramType == Integer.class) {
            argument = Integer.valueOf(propValue);
        } else if (paramType == long.class || paramType == Long.class) {
            argument = Long.valueOf(propValue);
        } else if (paramType == float.class || paramType == Float.class) {
            argument = Float.valueOf(propValue);
        } else if (paramType == double.class || paramType == Double.class) {
            argument = Double.valueOf(propValue);
        } else if (paramType == char.class || paramType == Character.class) {
            argument = Character.valueOf(propValue.length() > 0 ? propValue.charAt(0) : 0);
        } else if (paramType == TimeZone.class) {
            argument = TimeZone.getTimeZone(propValue);
        } else if (paramType == Charset.class) {
            argument = Charset.forName(propValue);
        } else {
            // ???
            throw new IllegalArgumentException("Unknown paramter type for property " + propertyName + " on " + objClass);
        }
        return argument;
    }

    private Handler configureHandler(final Properties properties, final String handlerName) throws IOException {
        if (configuredHandlers.containsKey(handlerName)) {
            return configuredHandlers.get(handlerName);
        }

        // Get handler class name, instantiate it
        final String handlerClassName = getStringProperty(properties, getKey("handler", handlerName));
        if (handlerClassName == null) {
            throw new IllegalArgumentException("Handler " + handlerName + " has no class name");
        }
        final Handler handler;
        try {
            handler = (Handler) Class.forName(handlerClassName).getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Handler " + handlerName + " could not be instantiated", e);
        }
        configuredHandlers.put(handlerName, handler);

        // Get handler level
        final String levelName = getStringProperty(properties, getKey("handler", handlerName, "level"));
        if (levelName != null) {
            handler.setLevel(LogContext.getSystemLogContext().getLevelForName(levelName));
        }

        // Get handler encoding
        final String encodingName = getStringProperty(properties, getKey("handler", handlerName, "encoding"));
        if (encodingName != null) {
            handler.setEncoding(encodingName);
        }

        // Get error handler
        final String errorManagerName = getStringProperty(properties, getKey("handler", handlerName, "errorManager"));
        if (errorManagerName != null) {
            handler.setErrorManager(configureErrorManager(properties, errorManagerName));
        }

        // Get filter
        final String filterName = getStringProperty(properties, getKey("handler", handlerName, "filter"));
        if (filterName != null) {
            handler.setFilter(configureFilter(properties, filterName));
        }

        // Get formatter
        final String formatterName = getStringProperty(properties, getKey("handler", handlerName, "formatter"));
        if (formatterName != null) {
            handler.setFormatter(configureFormatter(properties, formatterName));
        }

        // Get properties
        configureProperties(properties, handler, getKey("handler", handlerName));

        return handler;
    }

    private ErrorManager configureErrorManager(final Properties properties, final String errorManagerName) throws IOException {
        if (configuredErrorManagers.containsKey(errorManagerName)) {
            return configuredErrorManagers.get(errorManagerName);
        }
        
        // Get error manager class name, instantiate it
        final String errorManagerClassName = getStringProperty(properties, getKey("errorManager", errorManagerName));
        if (errorManagerClassName == null) {
            throw new IllegalArgumentException("Error manager " + errorManagerName + " has no class name");
        }
        final ErrorManager errorManager;
        try {
            errorManager = (ErrorManager) Class.forName(errorManagerClassName).getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Error manager " + errorManagerName + " could not be instantiated", e);
        }
        configuredErrorManagers.put(errorManagerName, errorManager);

        // Get properties
        configureProperties(properties, errorManager, getKey("errorManager", errorManagerName));

        return errorManager;
    }

    private Formatter configureFormatter(final Properties properties, final String formatterName) throws IOException {
        if (configuredFormatters.containsKey(formatterName)) {
            return configuredFormatters.get(formatterName);
        }

        // Get formatter class name, instantiate it
        final String formatterClassName = getStringProperty(properties, getKey("formatter", formatterName));
        if (formatterClassName == null) {
            throw new IllegalArgumentException("Formatter " + formatterName + " has no class name");
        }
        final Formatter formatter;
        try {
            formatter = (Formatter) Class.forName(formatterClassName).getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Formatter " + formatterName + " could not be instantiated", e);
        }
        configuredFormatters.put(formatterName, formatter);

        // Get properties
        configureProperties(properties, formatter, getKey("formatter", formatterName));

        return formatter;
    }

    private Filter configureFilter(final Properties properties, final String filterName) throws IOException {
        if (configuredFilters.containsKey(filterName)) {
            return configuredFilters.get(filterName);
        }

        // Get filter class name, instantiate it
        final String filterClassName = getStringProperty(properties, getKey("filter", filterName));
        if (filterClassName == null) {
            throw new IllegalArgumentException("Filter " + filterName + " has no class name");
        }
        final Filter filter;
        try {
            filter = (Filter) Class.forName(filterClassName).getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Filter " + filterName + " could not be instantiated", e);
        }
        configuredFilters.put(filterName, filter);

        // Get properties
        configureProperties(properties, filter, getKey("filter", filterName));

        return filter;
    }

    private static String getKey(final String prefix, final String objectName) {
        return objectName.length() > 0 ? prefix + "." + objectName : prefix;
    }

    private static String getKey(final String prefix, final String objectName, final String key) {
        return objectName.length() > 0 ? prefix + "." + objectName + "." + key : prefix + "." + key;
    }

    private static String getStringProperty(final Properties properties, final String key) {
        final String val = properties.getProperty(key);
        return val == null ? null : replaceProperties(val);
    }

    private static String[] getStringCsvArray(final Properties properties, final String key) {
        final String value = properties.getProperty(key, "").trim();
        if (value.length() == 0) {
            return EMPTY_STRINGS;
        }
        final String realValue = replaceProperties(value);
        return realValue.split("\\s*,\\s*");
    }

    private static List<String> getStringCsvList(final Properties properties, final String key) {
        return new ArrayList<String>(Arrays.asList(getStringCsvArray(properties, key)));
    }

    private static List<String> getStringCsvList(final Properties properties, final String key, final String... prepend) {
        final String[] array = getStringCsvArray(properties, key);
        final List<String> list = new ArrayList<String>(array.length + prepend.length);
        list.addAll(Arrays.asList(prepend));
        list.addAll(Arrays.asList(array));
        return list;
    }

    private static void safeClose(final Closeable stream) {
        if (stream != null) try {
            stream.close();
        } catch (Exception e) {
            // can't do anything about it
        }
    }

    private static final int INITIAL = 0;
    private static final int GOT_DOLLAR = 1;
    private static final int GOT_OPEN_BRACE = 2;
    private static final int RESOLVED = 3;
    private static final int DEFAULT = 4;

    /**
     * Replace properties of the form:
     * <code>${<i>&lt;name&gt;[</i>,<i>&lt;name2&gt;[</i>,<i>&lt;name3&gt;...]][</i>:<i>&lt;default&gt;]</i>}</code>
     * @param value
     * @return
     */
    private static String replaceProperties(String value) {
        final StringBuilder builder = new StringBuilder();
        final char[] chars = value.toCharArray();
        final int len = chars.length;
        int state = 0;
        int start = -1;
        int nameStart = -1;
        for (int i = 0; i < len; i ++) {
            char ch = chars[i];
            switch (state) {
                case INITIAL: {
                    switch (ch) {
                        case '$': {
                            state = GOT_DOLLAR;
                            continue;
                        }
                        default: {
                            builder.append(ch);
                            continue;
                        }
                    }
                    // not reachable
                }
                case GOT_DOLLAR: {
                    switch (ch) {
                        case '$': {
                            builder.append(ch);
                            state = INITIAL;
                            continue;
                        }
                        case '{': {
                            start = i + 1;
                            nameStart = start;
                            state = GOT_OPEN_BRACE;
                            continue;
                        }
                        default: {
                            // invalid; emit and resume
                            builder.append('$').append(ch);
                            state = INITIAL;
                            continue;
                        }
                    }
                    // not reachable
                }
                case GOT_OPEN_BRACE: {
                    switch (ch) {
                        case ':':
                        case '}':
                        case ',': {
                            final String name = value.substring(nameStart, i).trim();
                            if ("/".equals(name)) {
                                builder.append(File.separator);
                                state = ch == '}' ? INITIAL : RESOLVED;
                                continue;
                            } else if (":".equals(name)) {
                                builder.append(File.pathSeparator);
                                state = ch == '}' ? INITIAL : RESOLVED;
                                continue;
                            }
                            final String val = System.getProperty(name);
                            if (val != null) {
                                builder.append(val);
                                state = ch == '}' ? INITIAL : RESOLVED;
                                continue;
                            } else if (ch == ',') {
                                nameStart = i + 1;
                                continue;
                            } else if (ch == ':') {
                                start = i + 1;
                                state = DEFAULT;
                                continue;
                            } else {
                                builder.append(value.substring(start - 2, i + 1));
                                state = INITIAL;
                                continue;
                            }
                        }
                        default: {
                            continue;
                        }
                    }
                    // not reachable
                }
                case RESOLVED: {
                    if (ch == '}') {
                        state = INITIAL;
                    }
                    continue;
                }
                case DEFAULT: {
                    if (ch == '}') {
                        state = INITIAL;
                        builder.append(value.substring(start, i));
                    }
                    continue;
                }
                default: throw new IllegalStateException();
            }
        }
        switch (state) {
            case GOT_DOLLAR: {
                builder.append('$');
                break;
            }
            case DEFAULT:
            case GOT_OPEN_BRACE: {
                builder.append(value.substring(start - 2));
                break;
            }
        }
        return builder.toString();
    }
}
