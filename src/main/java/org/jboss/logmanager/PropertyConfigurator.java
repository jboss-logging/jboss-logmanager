/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import org.jboss.logmanager.config.ErrorManagerConfiguration;
import org.jboss.logmanager.config.FilterConfiguration;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.jboss.logmanager.config.PropertyConfigurable;

/**
 * A configurator which uses a simple property file format.
 */
public final class PropertyConfigurator implements Configurator {

    private static final String[] EMPTY_STRINGS = new String[0];

    private final LogContextConfiguration config;

    /**
     * Construct an instance.
     */
    public PropertyConfigurator() {
        this(LogContext.getSystemLogContext());
    }

    /**
     * Construct a new instance.
     *
     * @param context the log context to be configured
     */
    public PropertyConfigurator(LogContext context) {
        config = LogContextConfiguration.Factory.create(context);
    }

    /**
     * Get the log context configuration.  <em>WARNING</em>: this instance is not thread safe in any way.  The returned
     * object should never be used from more than one thread at a time; furthermore the {@link #writeConfiguration(java.io.OutputStream)}
     * method also accesses this object directly.
     *
     * @return the log context configuration instance
     */
    public LogContextConfiguration getLogContextConfiguration() {
        return config;
    }

    /** {@inheritDoc} */
    public void configure(final InputStream inputStream) throws IOException {
        final Properties properties = new Properties();
        try {
            properties.load(new InputStreamReader(inputStream, "utf-8"));
            inputStream.close();
        } finally {
            safeClose(inputStream);
        }
        configure(properties);
    }

    public void writeConfiguration(final OutputStream outputStream) throws IOException {
        final Writer writer = new OutputStreamWriter(outputStream, "utf-8");
        final Set<String> implicitHandlers = new HashSet<String>();
        final Set<String> implicitFormatters = new HashSet<String>();
        final Set<String> implicitErrorManagers = new HashSet<String>();
        final List<String> loggerNames = config.getLoggerNames();
        writer.write("# Additional loggers to configure (the root logger is always configured)\n");
        writer.write("loggers=");
        writeList(writer, loggerNames);
        writer.write('\n');
        final LoggerConfiguration rootLogger = config.getLoggerConfiguration("");
        writeLoggerConfiguration(writer, rootLogger, implicitHandlers);
        for (String loggerName : loggerNames) {
            writeLoggerConfiguration(writer, config.getLoggerConfiguration(loggerName), implicitHandlers);
        }
        final List<String> allHandlerNames = config.getHandlerNames();
        final List<String> explicitHandlerNames = new ArrayList<String>(allHandlerNames);
        explicitHandlerNames.removeAll(implicitHandlers);
        if (! explicitHandlerNames.isEmpty()) {
            writer.write("\n# Additional handlers to configure\n");
            writer.write("handlers=");
            writeList(writer, explicitHandlerNames);
            writer.write('\n');
            writer.write('\n');
        }
        for (String handlerName : allHandlerNames) {
            writeHandlerConfiguration(writer, config.getHandlerConfiguration(handlerName), implicitHandlers, implicitFormatters, implicitErrorManagers);
        }
        final List<String> allFilterNames = config.getFilterNames();
        if (! allFilterNames.isEmpty()) {
            writer.write("\n# Additional filters to configure\n");
            writer.write("filters=");
            writeList(writer, allFilterNames);
            writer.write('\n');
            writer.write('\n');
        }
        for (String filterName : allFilterNames) {
            writeFilterConfiguration(writer, config.getFilterConfiguration(filterName));
        }
        final List<String> allFormatterNames = config.getFormatterNames();
        final ArrayList<String> explicitFormatterNames = new ArrayList<String>(allFormatterNames);
        explicitFormatterNames.removeAll(implicitFormatters);
        if (! explicitFormatterNames.isEmpty()) {
            writer.write("\n# Additional formatters to configure\n");
            writer.write("formatters=");
            writeList(writer, explicitFormatterNames);
            writer.write('\n');
            writer.write('\n');
        }
        for (String formatterName : allFormatterNames) {
            writeFormatterConfiguration(writer, config.getFormatterConfiguration(formatterName));
        }
        final List<String> allErrorManagerNames = config.getErrorManagerNames();
        final ArrayList<String> explicitErrorManagerNames = new ArrayList<String>(allErrorManagerNames);
        explicitErrorManagerNames.removeAll(implicitErrorManagers);
        if (! explicitErrorManagerNames.isEmpty()) {
            writer.write("\n# Additional errorManagers to configure\n");
            writer.write("errorManagers=");
            writeList(writer, explicitErrorManagerNames);
            writer.write('\n');
            writer.write('\n');
        }
        for (String errorManagerName : allErrorManagerNames) {
            writeErrorManagerConfiguration(writer, config.getErrorManagerConfiguration(errorManagerName));
        }
    }

    private static void writeLoggerConfiguration(final Writer writer, final LoggerConfiguration logger, final Set<String> implicitHandlers) throws IOException {
        if (logger != null) {
            writer.write('\n');
            final String name = logger.getName();
            final String prefix = name.isEmpty() ? "logger." : "logger." + name + ".";
            final String level = logger.getLevel();
            if (level != null) {
                writer.write(prefix);
                writer.write("level=");
                writer.write(level);
                writer.write('\n');
            }
            final String filterName = logger.getFilterName();
            if (filterName != null) {
                writer.write(prefix);
                writer.write("filter=");
                writer.write(filterName);
                writer.write('\n');
            }
            final Boolean useParentHandlers = logger.getUseParentHandlers();
            if (useParentHandlers != null) {
                writer.write(prefix);
                writer.write("useParentHandlers=");
                writer.write(useParentHandlers.toString());
                writer.write('\n');
            }
            final List<String> handlerNames = logger.getHandlerNames();
            if (! handlerNames.isEmpty()) {
                writer.write(prefix);
                writer.write("handlers=");
                writeList(writer, handlerNames);
                writer.write('\n');
                for (String handlerName : handlerNames) {
                    implicitHandlers.add(handlerName);
                }
            }
        }
    }

    private static void writeHandlerConfiguration(final Writer writer, final HandlerConfiguration handler, final Set<String> implicitHandlers, final Set<String> implicitFormatters, final Set<String> implicitErrorManagers) throws IOException {
        if (handler != null) {
            writer.write('\n');
            final String name = handler.getName();
            final String prefix = "handler." + name + ".";
            final String className = handler.getClassName();
            writer.write("handler.");
            writer.write(name);
            writer.write('=');
            writer.write(className);
            writer.write('\n');
            final String moduleName = handler.getModuleName();
            if (moduleName != null) {
                writer.write(prefix);
                writer.write("module=");
                writer.write(moduleName);
                writer.write('\n');
            }
            final String level = handler.getLevel();
            if (level != null) {
                writer.write(prefix);
                writer.write("level=");
                writer.write(level);
                writer.write('\n');
            }
            final String filter = handler.getFilter();
            if (filter != null) {
                writer.write(prefix);
                writer.write("filter=");
                writer.write(filter);
                writer.write('\n');
            }
            final String formatterName = handler.getFormatterName();
            if (formatterName != null) {
                writer.write(prefix);
                writer.write("formatter=");
                writer.write(formatterName);
                writer.write('\n');
                implicitFormatters.add(formatterName);
            }
            final String errorManagerName = handler.getErrorManagerName();
            if (errorManagerName != null) {
                writer.write(prefix);
                writer.write("errorManager=");
                writer.write(errorManagerName);
                writer.write('\n');
                implicitErrorManagers.add(errorManagerName);
            }
            final List<String> handlerNames = handler.getHandlerNames();
            if (! handlerNames.isEmpty()) {
                writer.write(prefix);
                writer.write("handlers=");
                writeList(writer, handlerNames);
                writer.write('\n');
                for (String handlerName : handlerNames) {
                    implicitHandlers.add(handlerName);
                }
            }
            final List<String> propertyNames = handler.getPropertyNames();
            if (! propertyNames.isEmpty()) {
                writer.write(prefix);
                writer.write("properties=");
                writeList(writer, propertyNames);
                writer.write('\n');
                for (String propertyName : propertyNames) {
                    writer.write(prefix);
                    writer.write(propertyName);
                    writer.write('=');
                    writer.write(handler.getPropertyValueString(propertyName));
                    writer.write('\n');
                }
            }
        }
    }

    private static void writeFilterConfiguration(final Writer writer, final FilterConfiguration filter) throws IOException {
        if (filter != null) {
            writer.write('\n');
            final String name = filter.getName();
            final String prefix = "filter." + name + ".";
            final String className = filter.getClassName();
            writer.write("filter.");
            writer.write(name);
            writer.write('=');
            writer.write(className);
            writer.write('\n');
            final String moduleName = filter.getModuleName();
            if (moduleName != null) {
                writer.write(prefix);
                writer.write("module=");
                writer.write(moduleName);
                writer.write('\n');
            }
            final List<String> propertyNames = filter.getPropertyNames();
            if (! propertyNames.isEmpty()) {
                writer.write(prefix);
                writer.write("properties=");
                writeList(writer, propertyNames);
                writer.write('\n');
                for (String propertyName : propertyNames) {
                    writer.write(prefix);
                    writer.write(propertyName);
                    writer.write('=');
                    writer.write(filter.getPropertyValueString(propertyName));
                    writer.write('\n');
                }
            }
        }
    }

    private static void writeFormatterConfiguration(final Writer writer, final FormatterConfiguration formatter) throws IOException {
        if (formatter != null) {
            writer.write('\n');
            final String name = formatter.getName();
            final String prefix = "formatter." + name + ".";
            final String className = formatter.getClassName();
            writer.write("formatter.");
            writer.write(name);
            writer.write('=');
            writer.write(className);
            writer.write('\n');
            final String moduleName = formatter.getModuleName();
            if (moduleName != null) {
                writer.write(prefix);
                writer.write("module=");
                writer.write(moduleName);
                writer.write('\n');
            }
            final List<String> propertyNames = formatter.getPropertyNames();
            if (! propertyNames.isEmpty()) {
                writer.write(prefix);
                writer.write("properties=");
                writeList(writer, propertyNames);
                writer.write('\n');
                for (String propertyName : propertyNames) {
                    writer.write(prefix);
                    writer.write(propertyName);
                    writer.write('=');
                    writer.write(formatter.getPropertyValueString(propertyName));
                    writer.write('\n');
                }
            }
        }
    }

    private static void writeErrorManagerConfiguration(final Writer writer, final ErrorManagerConfiguration errorManager) throws IOException {
        if (errorManager != null) {
            writer.write('\n');
            final String name = errorManager.getName();
            final String prefix = "errorManager." + name + ".";
            final String className = errorManager.getClassName();
            writer.write("errorManager.");
            writer.write(name);
            writer.write('=');
            writer.write(className);
            writer.write('\n');
            final String moduleName = errorManager.getModuleName();
            if (moduleName != null) {
                writer.write(prefix);
                writer.write("module=");
                writer.write(moduleName);
                writer.write('\n');
            }
            final List<String> propertyNames = errorManager.getPropertyNames();
            if (! propertyNames.isEmpty()) {
                writer.write(prefix);
                writer.write("properties=");
                writeList(writer, propertyNames);
                writer.write('\n');
                for (String propertyName : propertyNames) {
                    writer.write(prefix);
                    writer.write(propertyName);
                    writer.write('=');
                    writer.write(errorManager.getPropertyValueString(propertyName));
                    writer.write('\n');
                }
            }
        }
    }

    private static void writeList(final Writer writer, final List<String> names) throws IOException {
        Iterator<String> iterator = names.iterator();
        while (iterator.hasNext()) {
            final String name = iterator.next();
            writer.write(name);
            if (iterator.hasNext()) {
                writer.write(',');
            }
        }
    }

    /**
     * Configure the log manager from the given properties.
     *
     * @param properties the properties
     * @throws IOException if an error occurs
     */
    public void configure(final Properties properties) throws IOException {
        try {
            // Start with the list of loggers to configure.  The root logger is always on the list.
            configureLogger(properties, "");
            // And, for each logger name, configure any filters, handlers, etc.
            for (String loggerName : getStringCsvArray(properties, "loggers")) {
                configureLogger(properties, loggerName);
            }
            // Configure any declared handlers.
            for (String handlerName : getStringCsvArray(properties, "handlers")) {
                configureHandler(properties, handlerName);
            }
            // Configure any declared filters.
            for (String filterName : getStringCsvArray(properties, "filters")) {
                configureFilter(properties, filterName);
            }
            // Configure any declared formatters.
            for (String formatterName : getStringCsvArray(properties, "formatters")) {
                configureFormatter(properties, formatterName);
            }
            // Configure any declared error managers.
            for (String errorManagerName : getStringCsvArray(properties, "errorManagers")) {
                configureErrorManager(properties, errorManagerName);
            }
            config.commit();
        } finally {
            config.forget();
        }
    }

    private void configureLogger(final Properties properties, final String loggerName) throws IOException {
        if (config.getLoggerConfiguration(loggerName) != null) {
            // duplicate
            return;
        }
        final LoggerConfiguration loggerConfiguration = config.addLoggerConfiguration(loggerName);

        // Get logger level
        final String levelName = getStringProperty(properties, getKey("logger", loggerName, "level"));
        if (levelName != null) {
            loggerConfiguration.setLevel(levelName);
        }

        // Get logger filter
        final String filterName = getStringProperty(properties, getKey("logger", loggerName, "filter"));
        if (filterName != null) {
            loggerConfiguration.setFilterName(filterName);
            configureFilter(properties, filterName);
        }

        // Get logger handlers
        final String[] handlerNames = getStringCsvArray(properties, getKey("logger", loggerName, "handlers"));
        loggerConfiguration.setHandlerNames(handlerNames);
        for (String name : handlerNames) {
            configureHandler(properties, name);
        }

        // Get logger properties
        final String useParentHandlersString = getStringProperty(properties, getKey("logger", loggerName, "useParentHandlers"));
        if (useParentHandlersString != null) {
            loggerConfiguration.setUseParentHandlers(Boolean.valueOf(Boolean.parseBoolean(useParentHandlersString)));
        }
    }

    private void configureFilter(final Properties properties, final String filterName) throws IOException {
        if (config.getFilterConfiguration(filterName) != null) {
            // already configured!
            return;
        }
        final FilterConfiguration configuration = config.addFilterConfiguration(
                getStringProperty(properties, getKey("filter", filterName, "module")),
                getStringProperty(properties, getKey("filter", filterName)),
                filterName,
                getStringCsvArray(properties, getKey("filter", filterName, "constructorProperties")));
        configureProperties(properties, configuration, getKey("filter", filterName));
    }

    private void configureFormatter(final Properties properties, final String formatterName) throws IOException {
        if (config.getFormatterConfiguration(formatterName) != null) {
            // already configured!
            return;
        }
        final FormatterConfiguration configuration = config.addFormatterConfiguration(
                getStringProperty(properties, getKey("formatter", formatterName, "module")),
                getStringProperty(properties, getKey("formatter", formatterName)),
                formatterName,
                getStringCsvArray(properties, getKey("formatter", formatterName, "constructorProperties")));
        configureProperties(properties, configuration, getKey("formatter", formatterName));
    }

    private void configureErrorManager(final Properties properties, final String errorManagerName) throws IOException {
        if (config.getErrorManagerConfiguration(errorManagerName) != null) {
            // already configured!
            return;
        }
        final ErrorManagerConfiguration configuration = config.addErrorManagerConfiguration(
                getStringProperty(properties, getKey("errorManager", errorManagerName, "module")),
                getStringProperty(properties, getKey("errorManager", errorManagerName)),
                errorManagerName,
                getStringCsvArray(properties, getKey("errorManager", errorManagerName, "constructorProperties")));
        configureProperties(properties, configuration, getKey("errorManager", errorManagerName));
    }

    private void configureHandler(final Properties properties, final String handlerName) throws IOException {
        if (config.getHandlerConfiguration(handlerName) != null) {
            // already configured!
            return;
        }
        final HandlerConfiguration configuration = config.addHandlerConfiguration(
                getStringProperty(properties, getKey("handler", handlerName, "module")),
                getStringProperty(properties, getKey("handler", handlerName)),
                handlerName,
                getStringCsvArray(properties, getKey("handler", handlerName, "constructorProperties")));
        final String filter = getStringProperty(properties, getKey("handler", handlerName, "filter"));
        if (filter != null) {
            configuration.setFilter(filter);
        }
        final String levelName = getStringProperty(properties, getKey("handler", handlerName, "level"));
        if (levelName != null) {
            configuration.setLevel(levelName);
        }
        final String formatterName = getStringProperty(properties, getKey("handler", handlerName, "formatter"));
        if (formatterName != null) {
            configuration.setFormatterName(formatterName);
            configureFormatter(properties, formatterName);
        }
        final String encoding = getStringProperty(properties, getKey("handler", handlerName, "encoding"));
        if (encoding != null) {
            configuration.setEncoding(encoding);
        }
        final String errorManagerName = getStringProperty(properties, getKey("handler", handlerName, "errorManager"));
        if (errorManagerName != null) {
            configuration.setErrorManagerName(errorManagerName);
            configureErrorManager(properties, errorManagerName);
        }
        final String[] handlerNames = getStringCsvArray(properties, getKey("handler", handlerName, "handlers"));
        configuration.setHandlerNames(handlerNames);
        for (String name : handlerNames) {
            configureHandler(properties, name);
        }
        configureProperties(properties, configuration, getKey("handler", handlerName));
    }

    private void configureProperties(final Properties properties, final PropertyConfigurable configurable, final String prefix) throws IOException {
        final List<String> propertyNames = getStringCsvList(properties, getKey(prefix, "properties"));
        for (String propertyName : propertyNames) {
            final String valueString = getStringProperty(properties, getKey(prefix, propertyName));
            if (valueString != null) configurable.setPropertyValueString(propertyName, valueString);
        }
    }

    private static String getKey(final String prefix, final String objectName) {
        return objectName.length() > 0 ? prefix + "." + objectName : prefix;
    }

    private static String getKey(final String prefix, final String objectName, final String key) {
        return objectName.length() > 0 ? prefix + "." + objectName + "." + key : prefix + "." + key;
    }

    private static String getStringProperty(final Properties properties, final String key) {
        return properties.getProperty(key);
    }

    private static String[] getStringCsvArray(final Properties properties, final String key) {
        final String property = properties.getProperty(key, "");
        if (property == null) {
            return EMPTY_STRINGS;
        }
        final String value = property.trim();
        if (value.length() == 0) {
            return EMPTY_STRINGS;
        }
        return value.split("\\s*,\\s*");
    }

    private static List<String> getStringCsvList(final Properties properties, final String key) {
        return new ArrayList<String>(Arrays.asList(getStringCsvArray(properties, key)));
    }

    private static void safeClose(final Closeable stream) {
        if (stream != null) try {
            stream.close();
        } catch (Exception e) {
            // can't do anything about it
        }
    }
}
