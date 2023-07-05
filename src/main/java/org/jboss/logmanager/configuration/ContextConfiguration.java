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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;

/**
 * A configuration which can be stored on a {@linkplain org.jboss.logmanager.LogContext log context} to store information
 * about the configured error managers, handlers, filters, formatters and objects that might be associated with a
 * configured object.
 * <p>
 * The {@link #addObject(String, Supplier)} can be used to allow objects to be set when configuring error managers,
 * handlers, filters and formatters.
 * </p>
 * <p>
 * If the {@linkplain Supplier supplier} os not already an instance of a {@link ConfigurationResource}, then it is
 * wrapped and considered a {@linkplain ConfigurationResource#of(Supplier) lazy resource}.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({ "UnusedReturnValue", "unused" })
public class ContextConfiguration implements AutoCloseable {
    public static final Logger.AttachmentKey<ContextConfiguration> CONTEXT_CONFIGURATION_KEY = new Logger.AttachmentKey<>();
    private final LogContext context;
    private final Map<String, ConfigurationResource<ErrorManager>> errorManagers;
    private final Map<String, ConfigurationResource<Filter>> filters;
    private final Map<String, ConfigurationResource<Formatter>> formatters;
    private final Map<String, ConfigurationResource<Handler>> handlers;
    private final Map<String, Logger> loggers;
    private final Map<String, ConfigurationResource<Object>> objects;

    /**
     * Creates a new context configuration.
     */
    public ContextConfiguration(final LogContext context) {
        this.context = context;
        errorManagers = new ConcurrentHashMap<>();
        handlers = new ConcurrentHashMap<>();
        formatters = new ConcurrentHashMap<>();
        filters = new ConcurrentHashMap<>();
        loggers = new ConcurrentHashMap<>();
        objects = new ConcurrentHashMap<>();
    }

    /**
     * Returns the {@linkplain LogContext context} for this configuration.
     *
     * @return the context for this configuration
     */
    public LogContext getContext() {
        return context;
    }

    /**
     * Adds a defined logger to the configuration. If the logger already exists in the configuration, it is replaced and
     * the old logger will be returned.
     *
     * @param logger the logger to add
     * @return the old logger or {@code null} if the old logger did not exist
     */
    public Logger addLogger(final Logger logger) {
        Objects.requireNonNull(logger, "The logger cannot be null");
        return loggers.put(logger.getName(), logger);
    }

    /**
     * Removes the logger from the context configuration.
     *
     * @param name the name of the logger
     *
     * @return the removed logger or {@code null} if the logger did not exist
     */
    public Logger removeLogger(final String name) {
        return loggers.remove(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Checks if the logger exists in this context.
     *
     * @param name the logger name
     *
     * @return {@code true} if the logger exists in this context, otherwise {@code false}
     */
    public boolean hasLogger(final String name) {
        return loggers.containsKey(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Gets or creates a logger
     *
     * @param name the name of the logger
     *
     * @return the logger
     */
    public Logger getLogger(final String name) {
        return loggers.computeIfAbsent(Objects.requireNonNull(name, "The name cannot be null"), s -> getContext().getLogger(s));
    }

    /**
     * Returns an unmodifiable set of the configured logger names
     *
     * @return an unmodified set of the logger names
     */
    public Set<String> getLoggers() {
        return Set.copyOf(loggers.keySet());
    }

    /**
     * Adds an error manager to the context configuration.
     *
     * @param name         the name for the error manager
     * @param errorManager the error manager to add
     *
     * @return the previous error manager associated with the name or {@code null} if one did not exist
     */
    public ConfigurationResource<ErrorManager> addErrorManager(final String name, final Supplier<ErrorManager> errorManager) {
        if (errorManager == null) {
            return removeErrorManager(name);
        }
        return errorManagers.putIfAbsent(Objects.requireNonNull(name, "The name cannot be null"),
                ConfigurationResource.of(errorManager));
    }

    /**
     * Removes the error manager from the context configuration.
     *
     * @param name the name of the error manager
     *
     * @return the error manager removed or {@code null} if the error manager did not exist
     */
    public ConfigurationResource<ErrorManager> removeErrorManager(final String name) {
        return errorManagers.remove(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Checks if the error manager exists with the name provided.
     *
     * @param name the name for the error manager
     *
     * @return {@code true} if the error manager exists in this context, otherwise {@code false}
     */
    public boolean hasErrorManager(final String name) {
        return errorManagers.containsKey(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Gets the error manager if it exists.
     *
     * @param name the name of the error manager
     *
     * @return the error manager or {@code null} if the error manager does not exist
     */
    public ErrorManager getErrorManager(final String name) {
        if (errorManagers.containsKey(Objects.requireNonNull(name, "The name cannot be null"))) {
            return errorManagers.get(name).get();
        }
        return null;
    }

    /**
     * Returns an unmodifiable map of the error managers and the suppliers used to create them.
     *
     * @return an unmodified map of the error managers
     */
    public Map<String, ConfigurationResource<ErrorManager>> getErrorManagers() {
        return Collections.unmodifiableMap(errorManagers);
    }

    /**
     * Adds a handler to the context configuration.
     *
     * @param name    the name for the handler
     * @param handler the handler to add
     *
     * @return the previous handler associated with the name or {@code null} if one did not exist
     */
    public ConfigurationResource<Handler> addHandler(final String name, final Supplier<Handler> handler) {
        if (handler == null) {
            return removeHandler(name);
        }
        return handlers.putIfAbsent(Objects.requireNonNull(name, "The name cannot be null"),
                ConfigurationResource.of(handler));
    }

    /**
     * Removes the handler from the context configuration.
     *
     * @param name the name of the handler
     *
     * @return the handler removed or {@code null} if the handler did not exist
     */
    public ConfigurationResource<Handler> removeHandler(final String name) {
        return handlers.remove(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Checks if the handler exists with the name provided.
     *
     * @param name the name for the handler
     *
     * @return {@code true} if the handler exists in this context, otherwise {@code false}
     */
    public boolean hasHandler(final String name) {
        return handlers.containsKey(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Gets the handler if it exists.
     *
     * @param name the name of the handler
     *
     * @return the handler or {@code null} if the handler does not exist
     */
    public Handler getHandler(final String name) {
        if (handlers.containsKey(Objects.requireNonNull(name, "The name cannot be null"))) {
            return handlers.get(name).get();
        }
        return null;
    }

    /**
     * Returns an unmodifiable map of the handlers and the suppliers used to create them.
     *
     * @return an unmodified map of the handlers
     */
    public Map<String, ConfigurationResource<Handler>> getHandlers() {
        return Collections.unmodifiableMap(handlers);
    }

    /**
     * Adds a formatter to the context configuration.
     *
     * @param name      the name for the formatter
     * @param formatter the formatter to add
     *
     * @return the previous formatter associated with the name or {@code null} if one did not exist
     */
    public ConfigurationResource<Formatter> addFormatter(final String name, final Supplier<Formatter> formatter) {
        if (formatter == null) {
            return removeFormatter(name);
        }
        return formatters.putIfAbsent(Objects.requireNonNull(name, "The name cannot be null"),
                ConfigurationResource.of(formatter));
    }

    /**
     * Removes the formatter from the context configuration.
     *
     * @param name the name of the formatter
     *
     * @return the formatter removed or {@code null} if the formatter did not exist
     */
    public ConfigurationResource<Formatter> removeFormatter(final String name) {
        return formatters.remove(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Checks if the formatter exists with the name provided.
     *
     * @param name the name for the formatter
     *
     * @return {@code true} if the formatter exists in this context, otherwise {@code false}
     */
    public boolean hasFormatter(final String name) {
        return formatters.containsKey(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Gets the formatter if it exists.
     *
     * @param name the name of the formatter
     *
     * @return the formatter or {@code null} if the formatter does not exist
     */
    public Formatter getFormatter(final String name) {
        if (formatters.containsKey(Objects.requireNonNull(name, "The name cannot be null"))) {
            return formatters.get(name).get();
        }
        return null;
    }

    /**
     * Returns an unmodifiable map of the formatters and the suppliers used to create them.
     *
     * @return an unmodified map of the formatters
     */
    public Map<String, Supplier<Formatter>> getFormatters() {
        return Collections.unmodifiableMap(formatters);
    }

    /**
     * Adds a filter to the context configuration.
     *
     * @param name   the name for the filter
     * @param filter the filter to add
     *
     * @return the previous filter associated with the name or {@code null} if one did not exist
     */
    public ConfigurationResource<Filter> addFilter(final String name, final Supplier<Filter> filter) {
        if (filter == null) {
            return removeFilter(name);
        }
        return filters.putIfAbsent(Objects.requireNonNull(name, "The name cannot be null"),
                ConfigurationResource.of(filter));
    }

    /**
     * Removes the filter from the context configuration.
     *
     * @param name the name of the filter
     *
     * @return the filter removed or {@code null} if the filter did not exist
     */
    public ConfigurationResource<Filter> removeFilter(final String name) {
        return filters.remove(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Checks if the filter exists with the name provided.
     *
     * @param name the name for the filter
     *
     * @return {@code true} if the filter exists in this context, otherwise {@code false}
     */
    public boolean hasFilter(final String name) {
        return filters.containsKey(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Gets the filter if it exists.
     *
     * @param name the name of the filter
     *
     * @return the filer or {@code null} if the filter does not exist
     */
    public Filter getFilter(final String name) {
        if (filters.containsKey(Objects.requireNonNull(name, "The name cannot be null"))) {
            return filters.get(name).get();
        }
        return null;
    }

    /**
     * Returns an unmodifiable map of the filters and the suppliers used to create them.
     *
     * @return an unmodified map of the filters
     */
    public Map<String, ConfigurationResource<Filter>> getFilters() {
        return Collections.unmodifiableMap(filters);
    }

    /**
     * Adds an object that can be used as a configuration property for another configuration type. This is used for
     * cases when an object cannot simply be converted from a string.
     *
     * @param name   the name for the configuration object
     * @param object the configuration object to add
     *
     * @return the previous configuration object associated with the name or {@code null} if one did not exist
     */
    public ConfigurationResource<Object> addObject(final String name, final Supplier<Object> object) {
        if (object == null) {
            return removeObject(name);
        }
        return objects.putIfAbsent(Objects.requireNonNull(name, "The name cannot be null"),
                ConfigurationResource.of(object));
    }

    /**
     * Removes the configuration object from the context configuration.
     *
     * @param name the name of the configuration object
     *
     * @return the configuration object removed or {@code null} if the configuration object did not exist
     */
    public ConfigurationResource<Object> removeObject(final String name) {
        return objects.remove(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Checks if the configuration object exists with the name provided.
     *
     * @param name the name for the configuration object
     *
     * @return {@code true} if the configuration object exists in this context, otherwise {@code false}
     */
    public boolean hasObject(final String name) {
        return objects.containsKey(Objects.requireNonNull(name, "The name cannot be null"));
    }

    /**
     * Gets the configuration object if it exists.
     *
     * @param name the name of the configuration object
     *
     * @return the configuration object or {@code null} if the configuration object does not exist
     */
    public Object getObject(final String name) {
        if (objects.containsKey(Objects.requireNonNull(name, "The name cannot be null"))) {
            return objects.get(name).get();
        }
        return null;
    }

    /**
     * Returns an unmodifiable map of the configuration objects and the suppliers used to create them.
     *
     * @return an unmodified map of the configuration objects
     */
    public Map<String, ConfigurationResource<Object>> getObjects() {
        return Collections.unmodifiableMap(objects);
    }

    @Override
    public void close() throws Exception {
        context.close();
        loggers.clear();
        closeResources(handlers);
        closeResources(filters);
        closeResources(formatters);
        closeResources(errorManagers);
        closeResources(objects);
    }

    private static void closeResources(final Map<String, ? extends ConfigurationResource<?>> resources) {
        final var iter = resources.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            iter.remove();
            try {
                entry.getValue().close();
            } catch (Throwable ignore) {
                // do nothing
            }
        }
    }

}
