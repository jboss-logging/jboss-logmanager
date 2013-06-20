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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.jboss.logmanager.config.ErrorManagerConfiguration;
import org.jboss.logmanager.config.FilterConfiguration;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.jboss.logmanager.config.PojoConfiguration;
import org.jboss.logmanager.config.PropertyConfigurable;
import org.jboss.logmanager.config.ValueExpression;

/**
 * A configurator which uses a simple property file format.
 */
public final class PropertyConfigurator implements Configurator {

    private static final String[] EMPTY_STRINGS = new String[0];
    private static final String ENCODING = "utf-8";
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(".*\\$\\{.*\\}.*");

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
            properties.load(new InputStreamReader(inputStream, ENCODING));
            inputStream.close();
        } finally {
            safeClose(inputStream);
        }
        configure(properties);
    }

    /**
     * Writes the current configuration to the output stream.
     *
     * <b>Note:</b> the output stream will be closed.
     *
     * @param outputStream     the output stream to write to.
     * @throws IOException if an error occurs while writing the configuration.
     */
    public void writeConfiguration(final OutputStream outputStream) throws IOException {
        writeConfiguration(outputStream, false);
    }

    /**
     * Writes the current configuration to the output stream.
     *
     * <b>Note:</b> the output stream will be closed.
     *
     * @param outputStream     the output stream to write to.
     * @param writeExpressions {@code true} if expressions should be written, {@code false} if the resolved value should
     *                                     be written
     * @throws IOException if an error occurs while writing the configuration.
     */
    public void writeConfiguration(final OutputStream outputStream, final boolean writeExpressions) throws IOException {
        try {
            final PrintStream out = new PrintStream(outputStream, true, ENCODING);
            try {
                final Set<String> implicitHandlers = new HashSet<String>();
                final Set<String> implicitFilters = new HashSet<String>();
                final Set<String> implicitFormatters = new HashSet<String>();
                final Set<String> implicitErrorManagers = new HashSet<String>();
                final List<String> loggerNames = config.getLoggerNames();
                writePropertyComment(out, "Additional loggers to configure (the root logger is always configured)");
                writeProperty(out, "loggers", toCsvString(loggerNames));
                final LoggerConfiguration rootLogger = config.getLoggerConfiguration("");
                writeLoggerConfiguration(out, rootLogger, implicitHandlers, implicitFilters, writeExpressions);
                // Remove the root loggers
                loggerNames.remove("");
                for (String loggerName : loggerNames) {
                    writeLoggerConfiguration(out, config.getLoggerConfiguration(loggerName), implicitHandlers, implicitFilters, writeExpressions);
                }
                final List<String> allHandlerNames = config.getHandlerNames();
                final List<String> explicitHandlerNames = new ArrayList<String>(allHandlerNames);
                explicitHandlerNames.removeAll(implicitHandlers);
                if (!explicitHandlerNames.isEmpty()) {
                    writePropertyComment(out, "Additional handlers to configure");
                    writeProperty(out, "handlers", toCsvString(explicitHandlerNames));
                    out.println();
                }
                for (String handlerName : allHandlerNames) {
                    writeHandlerConfiguration(out, config.getHandlerConfiguration(handlerName), implicitHandlers, implicitFilters,
                            implicitFormatters, implicitErrorManagers, writeExpressions);
                }
                final List<String> allFilterNames = config.getFilterNames();
                final List<String> explicitFilterNames = new ArrayList<String>(allFilterNames);
                explicitFilterNames.removeAll(implicitFilters);
                if (!explicitFilterNames.isEmpty()) {
                    writePropertyComment(out, "Additional filters to configure");
                    writeProperty(out, "filters", toCsvString(explicitFilterNames));
                    out.println();
                }
                for (String filterName : allFilterNames) {
                    writeFilterConfiguration(out, config.getFilterConfiguration(filterName), writeExpressions);
                }
                final List<String> allFormatterNames = config.getFormatterNames();
                final ArrayList<String> explicitFormatterNames = new ArrayList<String>(allFormatterNames);
                explicitFormatterNames.removeAll(implicitFormatters);
                if (!explicitFormatterNames.isEmpty()) {
                    writePropertyComment(out, "Additional formatters to configure");
                    writeProperty(out, "formatters", toCsvString(explicitFormatterNames));
                    out.println();
                }
                for (String formatterName : allFormatterNames) {
                    writeFormatterConfiguration(out, config.getFormatterConfiguration(formatterName), writeExpressions);
                }
                final List<String> allErrorManagerNames = config.getErrorManagerNames();
                final ArrayList<String> explicitErrorManagerNames = new ArrayList<String>(allErrorManagerNames);
                explicitErrorManagerNames.removeAll(implicitErrorManagers);
                if (!explicitErrorManagerNames.isEmpty()) {
                    writePropertyComment(out, "Additional errorManagers to configure");
                    writeProperty(out, "errorManagers", toCsvString(explicitErrorManagerNames));
                    out.println();
                }
                for (String errorManagerName : allErrorManagerNames) {
                    writeErrorManagerConfiguration(out, config.getErrorManagerConfiguration(errorManagerName), writeExpressions);
                }

                // Write POJO configurations
                final List<String> pojoNames = config.getPojoNames();
                if (!pojoNames.isEmpty()) {
                    writePropertyComment(out, "POJOs to configure");
                    writeProperty(out, "pojos", toCsvString(pojoNames));
                    for (String pojoName : pojoNames) {
                        writePojoConfiguration(out, config.getPojoConfiguration(pojoName), writeExpressions);
                    }
                }

                out.close();
            } finally {
                safeClose(out);
            }
            outputStream.close();
        } finally {
            safeClose(outputStream);
        }
    }

    private void writeLoggerConfiguration(final PrintStream out, final LoggerConfiguration logger,
                                          final Set<String> implicitHandlers, final Set<String> implicitFilters,
                                          final boolean writeExpressions) {
        if (logger != null) {
            out.println();
            final String name = logger.getName();
            final String prefix = name.isEmpty() ? "logger." : "logger." + name + ".";
            final String level = (writeExpressions ? logger.getLevelValueExpression().getValue() : logger.getLevel());
            if (level != null) {
                writeProperty(out, prefix, "level", level);
            }
            final String filterName = (writeExpressions ? logger.getFilterValueExpression().getValue() : logger.getFilter());
            if (filterName != null) {
                writeProperty(out, prefix, "filter", filterName);
                implicitFilters.add(logger.getFilter());
            }
            final Boolean useParentHandlers = logger.getUseParentHandlers();
            final String useParentHandlersValue = (writeExpressions ? logger.getUseParentHandlersValueExpression().getValue() :
                    useParentHandlers == null ? null : useParentHandlers.toString());
            if (useParentHandlersValue != null) {
                writeProperty(out, prefix, "useParentHandlers", useParentHandlersValue);
            }
            final List<String> handlerNames = new ArrayList<String>();
            for (String handlerName : logger.getHandlerNames()) {
                if (config.getHandlerNames().contains(handlerName)) {
                    implicitHandlers.add(handlerName);
                    handlerNames.add(handlerName);
                } else {
                    printError("Handler %s is not defined and will not be written to the configuration for logger %s%n", handlerName, (name.isEmpty() ? "ROOT" : name));
                }
            }
            if (!handlerNames.isEmpty()) {
                writeProperty(out, prefix, "handlers", toCsvString(handlerNames));
            }
        }
    }

    private void writeHandlerConfiguration(final PrintStream out, final HandlerConfiguration handler,
                                           final Set<String> implicitHandlers, final Set<String> implicitFilters,
                                           final Set<String> implicitFormatters, final Set<String> implicitErrorManagers,
                                           final boolean writeExpressions) {
        if (handler != null) {
            out.println();
            final String name = handler.getName();
            final String prefix = "handler." + name + ".";
            final String className = handler.getClassName();
            writeProperty(out, "handler.", name, className);
            final String moduleName = handler.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final String level = (writeExpressions ? handler.getLevelValueExpression().getValue() : handler.getLevel());
            if (level != null) {
                writeProperty(out, prefix, "level", level);
            }
            final String encoding = (writeExpressions ? handler.getEncodingValueExpression().getValue() : handler.getEncoding());
            if (encoding != null) {
                writeProperty(out, prefix, "encoding", encoding);
            }
            final String filter = (writeExpressions ? handler.getFilterValueExpression().getValue() : handler.getFilter());
            if (filter != null) {
                writeProperty(out, prefix, "filter", filter);
                implicitFilters.add(handler.getFilter());
            }
            final String formatterName = (writeExpressions ? handler.getFormatterNameValueExpression().getValue() : handler.getFormatterName());
            if (formatterName != null) {
                // Make sure the formatter exists
                if (config.getFormatterNames().contains(handler.getFormatterName())) {
                    writeProperty(out, prefix, "formatter", formatterName);
                    implicitFormatters.add(handler.getFormatterName());
                } else {
                    printError("Formatter %s is not defined and will not be written to the configuration for handler %s%n", formatterName, name);
                }
            }
            final String errorManagerName = (writeExpressions ? handler.getErrorManagerNameValueExpression().getValue() : handler.getErrorManagerName());
            if (errorManagerName != null) {
                // Make sure the error manager exists
                if (config.getErrorManagerNames().contains(handler.getErrorManagerName())) {
                    writeProperty(out, prefix, "errorManager", errorManagerName);
                    implicitErrorManagers.add(handler.getErrorManagerName());
                } else {
                    printError("Error manager %s is not defined and will not be written to the configuration for handler %s%n", errorManagerName, name);
                }
            }
            final List<String> handlerNames = new ArrayList<String>();
            for (String handlerName : handler.getHandlerNames()) {
                if (config.getHandlerNames().contains(handlerName)) {
                    implicitHandlers.add(handlerName);
                    handlerNames.add(handlerName);
                } else {
                    printError("Handler %s is not defined and will not be written to the configuration for handler %s%n", handlerName, name);
                }
            }
            if (!handlerNames.isEmpty()) {
                writeProperty(out, prefix, "handlers", toCsvString(handlerNames));
            }
            final List<String> postConfigurationMethods = handler.getPostConfigurationMethods();
            if (! postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, handler, writeExpressions);
        }
    }

    private static void writeFilterConfiguration(final PrintStream out, final FilterConfiguration filter, final boolean writeExpressions) {
        if (filter != null) {
            out.println();
            final String name = filter.getName();
            final String prefix = "filter." + name + ".";
            final String className = filter.getClassName();
            writeProperty(out, "filter.", name, className);
            final String moduleName = filter.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final List<String> postConfigurationMethods = filter.getPostConfigurationMethods();
            if (! postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, filter, writeExpressions);
        }
    }

    private static void writeFormatterConfiguration(final PrintStream out, final FormatterConfiguration formatter, final boolean writeExpressions) {
        if (formatter != null) {
            out.println();
            final String name = formatter.getName();
            final String prefix = "formatter." + name + ".";
            final String className = formatter.getClassName();
            writeProperty(out, "formatter.", name, className);
            final String moduleName = formatter.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final List<String> postConfigurationMethods = formatter.getPostConfigurationMethods();
            if (! postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, formatter, writeExpressions);
        }
    }

    private static void writeErrorManagerConfiguration(final PrintStream out, final ErrorManagerConfiguration errorManager, final boolean writeExpressions) {
        if (errorManager != null) {
            out.println();
            final String name = errorManager.getName();
            final String prefix = "errorManager." + name + ".";
            final String className = errorManager.getClassName();
            writeProperty(out, "errorManager.", name, className);
            final String moduleName = errorManager.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final List<String> postConfigurationMethods = errorManager.getPostConfigurationMethods();
            if (! postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, errorManager, writeExpressions);
        }
    }

    private static void writePojoConfiguration(final PrintStream out, final PojoConfiguration pojo, final boolean writeExpressions) {
        if (pojo != null) {
            out.println();
            final String name = pojo.getName();
            final String prefix = "pojo." + name + ".";
            final String className = pojo.getClassName();
            writeProperty(out, "pojo.", name, className);
            final String moduleName = pojo.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final List<String> postConfigurationMethods = pojo.getPostConfigurationMethods();
            if (! postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, pojo, writeExpressions);
        }
    }

    /**
     * Writes a comment to the print stream. Prepends the comment with a {@code #}.
     *
     * @param out     the print stream to write to.
     * @param comment the comment to write.
     */
    private static void writePropertyComment(final PrintStream out, final String comment) {
        out.printf("%n# %s%n", comment);
    }

    /**
     * Writes a property to the print stream.
     *
     * @param out    the print stream to write to.
     * @param name   the name of the property.
     * @param value  the value of the property.
     */
    private static void writeProperty(final PrintStream out, final String name, final String value) {
        writeProperty(out, null, name, value);
    }

    /**
     * Writes a property to the print stream.
     *
     * @param out    the print stream to write to.
     * @param prefix the prefix for the name or {@code null} to use no prefix.
     * @param name   the name of the property.
     * @param value  the value of the property.
     */
    private static void writeProperty(final PrintStream out, final String prefix, final String name, final String value) {
        if (prefix == null) {
            writeKey(out, name);
        } else {
            writeKey(out, String.format("%s%s", prefix, name));
        }
        writeValue(out, value);
        out.println();
    }

    /**
     * Writes a collection of properties to the print stream. Uses the {@link org.jboss.logmanager.config.PropertyConfigurable#getPropertyValueString(String)}
     * to extract the value.
     *
     * @param out                  the print stream to write to.
     * @param prefix               the prefix for the name or {@code null} to use no prefix.
     * @param propertyConfigurable the configuration to extract the property value from.
     * @param writeExpression      {@code true} if expressions should be written, {@code false} if the resolved value
     *                             should be written
     */
    private static void writeProperties(final PrintStream out, final String prefix, final PropertyConfigurable propertyConfigurable, final boolean writeExpression) {
        final List<String> names = propertyConfigurable.getPropertyNames();
        if (!names.isEmpty()) {
            final List<String> ctorProps = propertyConfigurable.getConstructorProperties();
            if (prefix == null) {
                writeProperty(out, "properties", toCsvString(names));
                if (!ctorProps.isEmpty()) {
                    writeProperty(out, "constructorProperties", toCsvString(ctorProps));
                }
                for (String name : names) {
                    if (writeExpression) {
                        writeProperty(out, name, propertyConfigurable.getPropertyValueExpression(name).getValue());
                    } else {
                        writeProperty(out, name, propertyConfigurable.getPropertyValueString(name));
                    }
                }
            } else {
                writeProperty(out, prefix, "properties", toCsvString(names));
                if (!ctorProps.isEmpty()) {
                    writeProperty(out, prefix, "constructorProperties", toCsvString(ctorProps));
                }
                for (String name : names) {
                    if (writeExpression) {
                        writeProperty(out, prefix, name, propertyConfigurable.getPropertyValueExpression(name).getValue());
                    } else {
                        writeProperty(out, prefix, name, propertyConfigurable.getPropertyValueString(name));
                    }
                }
            }
        }
    }

    /**
     * Parses the list and creates a comma delimited string of the names.
     * <p/>
     * <b>Notes:</b> empty names are ignored.
     *
     * @param names the names to process.
     *
     * @return a comma delimited list of the names.
     */
    private static String toCsvString(final List<String> names) {
        final StringBuilder result = new StringBuilder(1024);
        Iterator<String> iterator = names.iterator();
        while (iterator.hasNext()) {
            final String name = iterator.next();
            // No need to write empty names
            if (!name.isEmpty()) {
                result.append(name);
                if (iterator.hasNext()) {
                    result.append(",");
                }
            }
        }
        return result.toString();
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
            // Configure POJOs
            for (String pojoName : getStringCsvArray(properties, "pojos")) {
                configurePojos(properties, pojoName);
            }
            config.commit();
        } finally {
            config.forget();
        }
    }

    private void configureLogger(final Properties properties, final String loggerName) {
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
            // TODO (jrp) this is not really the best way to handle filters -
            // the trouble is the filter could be an expression, match("value"), or a defined filter
            loggerConfiguration.setFilter(filterName);
            final String resolvedFilter = loggerConfiguration.getFilterValueExpression().getResolvedValue();
            // Check for a filter class
            final String filterClassName = getStringProperty(properties, getKey("filter", resolvedFilter));
            // If the filter class is null, assume it's a filter expression
            if (filterClassName != null) {
                configureFilter(properties, resolvedFilter);
            }
        }

        // Get logger handlers
        final String[] handlerNames = getStringCsvArray(properties, getKey("logger", loggerName, "handlers"));
        for (String name : handlerNames) {
            if (configureHandler(properties, name)) {
                loggerConfiguration.addHandlerName(name);
            }
        }

        // Get logger properties
        final String useParentHandlersString = getStringProperty(properties, getKey("logger", loggerName, "useParentHandlers"));
        if (useParentHandlersString != null) {
            // Check for expression
            if (EXPRESSION_PATTERN.matcher(useParentHandlersString).matches()) {
                loggerConfiguration.setUseParentHandlers(useParentHandlersString);
            } else {
                loggerConfiguration.setUseParentHandlers(Boolean.parseBoolean(useParentHandlersString));
            }
        }
    }

    private boolean configureFilter(final Properties properties, final String filterName) {
        if (config.getFilterConfiguration(filterName) != null) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(properties, getKey("filter", filterName));
        if (className == null) {
            printError("Filter %s is not defined%n", filterName);
            return false;
        }
        final FilterConfiguration configuration = config.addFilterConfiguration(
                getStringProperty(properties, getKey("filter", filterName, "module")),
                className,
                filterName,
                getStringCsvArray(properties, getKey("filter", filterName, "constructorProperties")));
        final String[] postConfigurationMethods = getStringCsvArray(properties, getKey("filter", filterName, "postConfiguration"));
        configuration.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(properties, configuration, getKey("filter", filterName));
        return true;
    }

    private boolean configureFormatter(final Properties properties, final String formatterName) {
        if (config.getFormatterConfiguration(formatterName) != null) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(properties, getKey("formatter", formatterName));
        if (className == null) {
            printError("Formatter %s is not defined%n", formatterName);
            return false;
        }
        final FormatterConfiguration configuration = config.addFormatterConfiguration(
                getStringProperty(properties, getKey("formatter", formatterName, "module")),
                className,
                formatterName,
                getStringCsvArray(properties, getKey("formatter", formatterName, "constructorProperties")));
        final String[] postConfigurationMethods = getStringCsvArray(properties, getKey("formatter", formatterName, "postConfiguration"));
        configuration.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(properties, configuration, getKey("formatter", formatterName));
        return true;
    }

    private boolean configureErrorManager(final Properties properties, final String errorManagerName) {
        if (config.getErrorManagerConfiguration(errorManagerName) != null) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(properties, getKey("errorManager", errorManagerName));
        if (className == null) {
            printError("Error manager %s is not defined%n", errorManagerName);
            return false;
        }
        final ErrorManagerConfiguration configuration = config.addErrorManagerConfiguration(
                getStringProperty(properties, getKey("errorManager", errorManagerName, "module")),
                className,
                errorManagerName,
                getStringCsvArray(properties, getKey("errorManager", errorManagerName, "constructorProperties")));
        final String[] postConfigurationMethods = getStringCsvArray(properties, getKey("errorManager", errorManagerName, "postConfiguration"));
        configuration.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(properties, configuration, getKey("errorManager", errorManagerName));
        return true;
    }

    private boolean configureHandler(final Properties properties, final String handlerName) {
        if (config.getHandlerConfiguration(handlerName) != null) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(properties, getKey("handler", handlerName));
        if (className == null) {
            printError("Handler %s is not defined%n", handlerName);
            return false;
        }
        final HandlerConfiguration configuration = config.addHandlerConfiguration(
                getStringProperty(properties, getKey("handler", handlerName, "module")),
                className,
                handlerName,
                getStringCsvArray(properties, getKey("handler", handlerName, "constructorProperties")));
        final String filter = getStringProperty(properties, getKey("handler", handlerName, "filter"));
        if (filter != null) {
            // TODO (jrp) this is not really the best way to handle filters -
            // the trouble is the filter could be an expression, match("value"), or a defined filter
            configuration.setFilter(filter);
            final String resolvedFilter = configuration.getFilterValueExpression().getResolvedValue();
            // Check for a filter class
            final String filterClassName = getStringProperty(properties, getKey("filter", resolvedFilter));
            // If the filter class is null, assume it's a filter expression
            if (filterClassName != null) {
                configureFilter(properties, resolvedFilter);
            }
        }
        final String levelName = getStringProperty(properties, getKey("handler", handlerName, "level"));
        if (levelName != null) {
            configuration.setLevel(levelName);
        }
        final String formatterName = getStringProperty(properties, getKey("handler", handlerName, "formatter"));
        if (formatterName != null) {
            if (getStringProperty(properties, getKey("formatter", ValueExpression.STRING_RESOLVER.resolve(formatterName).getResolvedValue())) == null) {
                printError("Formatter %s is not defined%n", formatterName);
            } else {
                configuration.setFormatterName(formatterName);
                configureFormatter(properties, configuration.getFormatterNameValueExpression().getResolvedValue());
            }
        }
        final String encoding = getStringProperty(properties, getKey("handler", handlerName, "encoding"));
        if (encoding != null) {
            configuration.setEncoding(encoding);
        }
        final String errorManagerName = getStringProperty(properties, getKey("handler", handlerName, "errorManager"));
        if (errorManagerName != null) {
            if (getStringProperty(properties, getKey("errorManager", ValueExpression.STRING_RESOLVER.resolve(errorManagerName).getResolvedValue())) == null) {
                printError("Error manager %s is not defined%n", errorManagerName);
            } else {
                configuration.setErrorManagerName(errorManagerName);
                configureErrorManager(properties, configuration.getErrorManagerNameValueExpression().getResolvedValue());
            }
        }
        final String[] handlerNames = getStringCsvArray(properties, getKey("handler", handlerName, "handlers"));
        for (String name : handlerNames) {
            if (configureHandler(properties, name)) {
                configuration.addHandlerName(name);
            }
        }
        final String[] postConfigurationMethods = getStringCsvArray(properties, getKey("handler", handlerName, "postConfiguration"));
        configuration.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(properties, configuration, getKey("handler", handlerName));
        return true;
    }

    private boolean configurePojos(final Properties properties, final String pojoName) {
        if (config.getPojoConfiguration(pojoName) != null) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(properties, getKey("pojo", pojoName));
        if (className == null) {
            printError("POJO %s is not defined%n", pojoName);
            return false;
        }
        final PojoConfiguration configuration = config.addPojoConfiguration(
                getStringProperty(properties, getKey("pojo", pojoName, "module")),
                getStringProperty(properties, getKey("pojo", pojoName)),
                pojoName,
                getStringCsvArray(properties, getKey("pojo", pojoName, "constructorProperties")));
        final String[] postConfigurationMethods = getStringCsvArray(properties, getKey("pojo", pojoName, "postConfiguration"));
        configuration.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(properties, configuration, getKey("pojo", pojoName));
        return true;
    }

    private void configureProperties(final Properties properties, final PropertyConfigurable configurable, final String prefix) {
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

    private static void writeValue(final PrintStream out, final String value) {
        writeSanitized(out, value, false);
    }

    private static void writeKey(final PrintStream out, final String key) {
        writeSanitized(out, key, true);
        out.append('=');
    }

    private static void writeSanitized(final PrintStream out, final String string, final boolean escapeSpaces) {
        for (int x = 0; x < string.length(); x++) {
            final char c = string.charAt(x);
            switch (c) {
                case ' ' :
                    if (x == 0 || escapeSpaces)
                        out.append('\\');
                    out.append(c);
                    break;
                case '\t':
                    out.append('\\').append('t');
                    break;
                case '\n':
                    out.append('\\').append('n');
                    break;
                case '\r':
                    out.append('\\').append('r');
                    break;
                case '\f':
                    out.append('\\').append('f');
                    break;
                case '\\':
                case '=':
                case ':':
                case '#':
                case '!':
                    out.append('\\').append(c);
                    break;
                default:
                    out.append(c);
            }
        }
    }

    /**
     * Prints the message to stderr.
     *
     * @param msg the message to print
     */
    static void printError(final String msg) {
        System.err.println(msg);
    }

    /**
     * Prints the message to stderr.
     *
     * @param format the format of the message
     * @param args   the format arguments
     */
    static void printError(final String format, final Object... args) {
        System.err.printf(format, args);
    }


    private static void safeClose(final Closeable stream) {
        if (stream != null) try {
            stream.close();
        } catch (Exception e) {
            // can't do anything about it
        }
    }
}
