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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.StandardOutputStreams;
import org.jboss.logmanager.configuration.filters.FilterExpressions;
import org.jboss.logmanager.filters.AcceptAllFilter;
import org.jboss.logmanager.filters.DenyAllFilter;

import io.smallrye.common.constraint.Assert;
import io.smallrye.common.expression.Expression;

/**
 * A utility to parse a {@code logging.properties} file and configure a {@link LogContext}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("WeakerAccess")
public class PropertyConfigurator {

    private static final String[] EMPTY_STRINGS = new String[0];

    private final LogContext logContext;
    private final Properties properties;
    private final ContextConfiguration contextConfiguration;

    private PropertyConfigurator(final LogContext logContext, final Properties properties) {
        this.logContext = logContext;
        this.properties = properties;
        ContextConfiguration config = new ContextConfiguration();
        final ContextConfiguration current = logContext.attachIfAbsent(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, config);
        contextConfiguration = (current == null ? config : current);
    }

    /**
     * Configures the {@link LogContext} based on the properties.
     *
     * @param logContext the log context to configure
     * @param properties the properties used to configure the log context
     */
    public static void configure(final LogContext logContext, final Properties properties) {
        final PropertyConfigurator config = new PropertyConfigurator(
                Assert.checkNotNullParam("logContext", logContext),
                Assert.checkNotNullParam("properties", properties));
        config.doConfigure();
    }

    private void doConfigure() {
        // POJO's must be configured first so other
        for (String pojoName : getStringCsvArray("pojos")) {
            configurePojos(pojoName);
        }
        // Start with the list of loggers to configure.  The root logger is always on the list.
        configureLogger("");
        // And, for each logger name, configure any filters, handlers, etc.
        for (String loggerName : getStringCsvArray("loggers")) {
            configureLogger(loggerName);
        }
        // Configure any declared handlers.
        for (String handlerName : getStringCsvArray("handlers")) {
            configureHandler(handlerName);
        }
        // Configure any declared filters.
        for (String filterName : getStringCsvArray("filters")) {
            configureFilter(filterName);
        }
        // Configure any declared formatters.
        for (String formatterName : getStringCsvArray("formatters")) {
            configureFormatter(formatterName);
        }
        // Configure any declared error managers.
        for (String errorManagerName : getStringCsvArray("errorManagers")) {
            configureErrorManager(errorManagerName);
        }
    }

    @SuppressWarnings({ "ConstantConditions", "CastCanBeRemovedNarrowingVariableType" })
    private void configureLogger(final String loggerName) {
        /*
         * if (logContext.getLoggerIfExists(loggerName) != null) {
         * // duplicate
         * return;
         * }
         */
        final Logger logger = logContext.getLogger(loggerName);

        // Get logger level
        final String levelName = getStringProperty(getKey("logger", loggerName, "level"));
        if (levelName != null) {
            logger.setLevel(Level.parse(levelName));
        }

        // Get logger filters
        final String filterName = getStringProperty(getKey("logger", loggerName, "filter"));
        if (filterName != null) {
            if (configureFilter(filterName)) {
                logger.setFilter(contextConfiguration.getFilter(filterName));
            }
        }

        // Get logger handlers
        final String[] handlerNames = getStringCsvArray(getKey("logger", loggerName, "handlers"));
        for (String name : handlerNames) {
            if (configureHandler(name)) {
                logger.addHandler(contextConfiguration.getHandler(name));
            }
        }

        // Get logger properties
        final String useParentHandlersString = getStringProperty(getKey("logger", loggerName, "useParentHandlers"));
        if (useParentHandlersString != null) {
            logger.setUseParentHandlers(resolveBooleanExpression(useParentHandlersString));
        }
        final String useParentFiltersString = getStringProperty(getKey("logger", loggerName, "useParentFilters"));
        if (useParentFiltersString != null) {
            if (logger instanceof org.jboss.logmanager.Logger) {
                ((org.jboss.logmanager.Logger) logger).setUseParentFilters(resolveBooleanExpression(useParentHandlersString));
            }
        }
    }

    private boolean configureHandler(final String handlerName) {
        if (contextConfiguration.hasHandler(handlerName)) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(getKey("handler", handlerName), true, false);
        if (className == null) {
            StandardOutputStreams.printError("Handler %s is not defined%n", handlerName);
            return false;
        }

        final ObjectBuilder<Handler> handlerBuilder = ObjectBuilder
                .of(logContext, contextConfiguration, Handler.class, className)
                .setModuleName(getStringProperty(getKey("handler", handlerName, "module")))
                .addPostConstructMethods(getStringCsvArray(getKey("handler", handlerName, "postConfiguration")));

        // Configure the constructor properties
        configureProperties(handlerBuilder, "handler", handlerName);
        final String encoding = getStringProperty(getKey("handler", handlerName, "encoding"));
        if (encoding != null) {
            handlerBuilder.addProperty("encoding", encoding);
        }

        final String filter = getStringProperty(getKey("handler", handlerName, "filter"));
        if (filter != null) {
            if (configureFilter(filter)) {
                handlerBuilder.addDefinedProperty("filter", Filter.class, contextConfiguration.getFilters().get(filter));
            }
        }
        final String levelName = getStringProperty(getKey("handler", handlerName, "level"));
        if (levelName != null) {
            handlerBuilder.addProperty("level", levelName);
        }
        final String formatterName = getStringProperty(getKey("handler", handlerName, "formatter"));
        if (formatterName != null) {
            if (configureFormatter(formatterName)) {
                handlerBuilder.addDefinedProperty("formatter", Formatter.class, contextConfiguration.getFormatters()
                        .get(formatterName));
            }
        }
        final String errorManagerName = getStringProperty(getKey("handler", handlerName, "errorManager"));
        if (errorManagerName != null) {
            if (configureErrorManager(errorManagerName)) {
                handlerBuilder.addDefinedProperty("errorManager", ErrorManager.class, contextConfiguration.getErrorManagers()
                        .get(errorManagerName));
            }
        }

        final String[] handlerNames = getStringCsvArray(getKey("handler", handlerName, "handlers"));
        if (handlerNames.length > 0) {
            final List<Supplier<Handler>> subhandlers = new ArrayList<>();
            for (String name : handlerNames) {
                if (configureHandler(name)) {
                    subhandlers.add(contextConfiguration.getHandlers().get(name));
                }
            }
            handlerBuilder.addDefinedProperty("handlers", Handler[].class, new Supplier<Handler[]>() {

                @Override
                public Handler[] get() {
                    if (subhandlers.isEmpty()) {
                        return new Handler[0];
                    }
                    final Handler[] result = new Handler[subhandlers.size()];
                    int i = 0;
                    for (Supplier<Handler> supplier : subhandlers) {
                        result[i++] = supplier.get();
                    }
                    return result;
                }
            });
        }
        contextConfiguration.addHandler(handlerName, handlerBuilder.build());
        return true;
    }

    private boolean configureFormatter(final String formatterName) {
        if (contextConfiguration.hasFilter(formatterName)) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(getKey("formatter", formatterName), true, false);
        if (className == null) {
            StandardOutputStreams.printError("Formatter %s is not defined%n", formatterName);
            return false;
        }
        final ObjectBuilder<Formatter> formatterBuilder = ObjectBuilder
                .of(logContext, contextConfiguration, Formatter.class, className)
                .setModuleName(getStringProperty(getKey("formatter", formatterName, "module")))
                .addPostConstructMethods(getStringCsvArray(getKey("formatter", formatterName, "postConfiguration")));
        configureProperties(formatterBuilder, "formatter", formatterName);
        contextConfiguration.addFormatter(formatterName, formatterBuilder.build());
        return true;
    }

    private boolean configureErrorManager(final String errorManagerName) {
        if (contextConfiguration.hasErrorManager(errorManagerName)) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(getKey("errorManager", errorManagerName), true, false);
        if (className == null) {
            StandardOutputStreams.printError("Error manager %s is not defined%n", errorManagerName);
            return false;
        }
        final ObjectBuilder<ErrorManager> errorManagerBuilder = ObjectBuilder
                .of(logContext, contextConfiguration, ErrorManager.class, className)
                .setModuleName(getStringProperty(getKey("errorManager", errorManagerName, "module")))
                .addPostConstructMethods(getStringCsvArray(getKey("errorManager", errorManagerName, "postConfiguration")));
        configureProperties(errorManagerBuilder, "errorManager", errorManagerName);
        contextConfiguration.addErrorManager(errorManagerName, errorManagerBuilder.build());
        return true;
    }

    private boolean configureFilter(final String filterName) {
        if (contextConfiguration.hasFilter(filterName)) {
            return true;
        }
        // First determine if we're using a defined filters or filters expression. We assume a defined filters if there is
        // a filters.NAME property.
        String filterValue = getStringProperty(getKey("filter", filterName), true, false);
        if (filterValue == null) {
            // We are a filters expression, parse the expression and create a filters
            contextConfiguration.addFilter(filterName, () -> FilterExpressions.parse(logContext, filterName));
        } else {
            // The AcceptAllFilter and DenyAllFilter are singletons.
            if (AcceptAllFilter.class.getName().equals(filterValue)) {
                contextConfiguration.addFilter(filterName, AcceptAllFilter::getInstance);
            } else if (DenyAllFilter.class.getName().equals(filterValue)) {
                contextConfiguration.addFilter(filterName, DenyAllFilter::getInstance);
            } else {
                // We assume we're a defined filter
                final ObjectBuilder<Filter> filterBuilder = ObjectBuilder
                        .of(logContext, contextConfiguration, Filter.class, filterValue)
                        .setModuleName(getStringProperty(getKey("filter", filterName, "module")))
                        .addPostConstructMethods(getStringCsvArray(getKey("filter", filterName, "postConfiguration")));
                configureProperties(filterBuilder, "errorManager", filterName);
                contextConfiguration.addFilter(filterName, filterBuilder.build());
            }
        }
        return true;
    }

    private void configurePojos(final String pojoName) {
        if (contextConfiguration.hasObject(pojoName)) {
            // already configured!
            return;
        }
        final String className = getStringProperty(getKey("pojo", pojoName), true, false);
        if (className == null) {
            StandardOutputStreams.printError("POJO %s is not defined%n", pojoName);
            return;
        }
        final ObjectBuilder<Object> pojoBuilder = ObjectBuilder.of(logContext, contextConfiguration, Object.class, className)
                .setModuleName(getStringProperty(getKey("pojo", pojoName, "module")))
                .addPostConstructMethods(getStringCsvArray(getKey("pojo", pojoName, "postConfiguration")));
        configureProperties(pojoBuilder, "pojo", pojoName);
        contextConfiguration.addObject(pojoName, pojoBuilder.build());
    }

    private String getStringProperty(final String key) {
        return getStringProperty(key, true, true);
    }

    private String getStringProperty(final String key, final boolean trim, final boolean resolveExpression) {
        String value = properties.getProperty(key);
        if (resolveExpression && value != null) {
            value = resolveExpression(value);
        } else if (value != null && trim) {
            value = value.trim();
        }
        return value;
    }

    private String[] getStringCsvArray(final String key) {
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

    private void configureProperties(final ObjectBuilder<?> builder, final String prefix, final String name) {
        // First configure constructor properties
        final String[] constructorPropertyNames = getStringCsvArray(getKey(prefix, name, "constructorProperties"));
        for (String propertyName : constructorPropertyNames) {
            final String valueString = getStringProperty(getKey(prefix, name, propertyName), false, true);
            if (valueString != null)
                builder.addConstructorProperty(propertyName, valueString);
        }

        // Next configure setter properties
        final String[] propertyNames = getStringCsvArray(getKey(prefix, name, "properties"));
        for (String propertyName : propertyNames) {
            final String valueString = getStringProperty(getKey(prefix, name, propertyName), false, true);
            if (valueString != null)
                builder.addProperty(propertyName, valueString);
        }
    }

    private static String getKey(final String prefix, final String objectName) {
        return objectName.length() > 0 ? prefix + "." + objectName : prefix;
    }

    private static String getKey(final String prefix, final String objectName, final String key) {
        return objectName.length() > 0 ? prefix + "." + objectName + "." + key : prefix + "." + key;
    }

    private static boolean resolveBooleanExpression(final String possibleExpression) {
        final String value = resolveExpression(possibleExpression);
        return !value.toLowerCase(Locale.ROOT).equals("false");
    }

    private static String resolveExpression(final String possibleExpression) {
        final EnumSet<Expression.Flag> flags = EnumSet.noneOf(Expression.Flag.class);
        final Expression expression = Expression.compile(possibleExpression, flags);
        return expression.evaluateWithPropertiesAndEnvironment(false);
    }
}
