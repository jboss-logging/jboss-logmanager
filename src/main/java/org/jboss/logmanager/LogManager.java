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

package org.jboss.logmanager;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Filter;

import org.jboss.logmanager.configuration.PropertyLogContextConfigurator;

import io.smallrye.common.constraint.Assert;

/**
 * Simplified log manager. Designed to work around the (many) design flaws of the JDK platform log manager.
 */
public final class LogManager extends java.util.logging.LogManager {

    public static final String PER_THREAD_LOG_FILTER_KEY = "org.jboss.logmanager.useThreadLocalFilter";
    static final boolean PER_THREAD_LOG_FILTER;

    static {
        if (System.getSecurityManager() == null) {
            try {
                // Ensure the StandardOutputStreams are initialized early to capture the current System.out and System.err.
                Class.forName(StandardOutputStreams.class.getName());
            } catch (ClassNotFoundException ignore) {
            }
            PER_THREAD_LOG_FILTER = Boolean.getBoolean(PER_THREAD_LOG_FILTER_KEY);
        } else {
            PER_THREAD_LOG_FILTER = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return Boolean.getBoolean(PER_THREAD_LOG_FILTER_KEY);
                }
            });

            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        // Ensure the StandardOutputStreams are initialized early to capture the current System.out and System.err.
                        Class.forName(StandardOutputStreams.class.getName());
                    } catch (ClassNotFoundException ignore) {
                    }
                    return null;
                }
            });
        }
    }

    private static class LocalFilterHolder {
        static final ThreadLocal<Filter> LOCAL_FILTER = new ThreadLocal<>();
    }

    /**
     * Construct a new logmanager instance. Attempts to plug a known memory leak in {@link java.util.logging.Level} as
     * well.
     */
    public LogManager() {
    }

    // Configuration

    private final AtomicReference<LogContextConfigurator> configuratorRef = new AtomicReference<>();

    /**
     * Configure the system log context initially.
     */
    public void readConfiguration() {
        doConfigure(null);
    }

    /**
     * Configure the system log context initially withe given input stream.
     *
     * @param inputStream ignored
     */
    public void readConfiguration(InputStream inputStream) {
        Assert.checkNotNullParam("inputStream", inputStream);
        doConfigure(inputStream);
    }

    private void doConfigure(InputStream inputStream) {
        final AtomicReference<LogContextConfigurator> configuratorRef = this.configuratorRef;
        LogContextConfigurator configurator = configuratorRef.get();
        if (configurator == null) {
            synchronized (configuratorRef) {
                configurator = configuratorRef.get();
                if (configurator == null) {
                    int best = Integer.MAX_VALUE;
                    ConfiguratorFactory factory = null;
                    final ServiceLoader<ConfiguratorFactory> serviceLoader = ServiceLoader.load(ConfiguratorFactory.class);
                    final Iterator<ConfiguratorFactory> iterator = serviceLoader.iterator();
                    List<Throwable> problems = null;
                    for (;;)
                        try {
                            if (!iterator.hasNext())
                                break;
                            final ConfiguratorFactory f = iterator.next();
                            if (f.priority() < best || factory == null) {
                                best = f.priority();
                                factory = f;
                            }
                        } catch (Throwable t) {
                            if (problems == null)
                                problems = new ArrayList<>(4);
                            problems.add(t);
                        }
                    configurator = factory == null ? null : factory.create();
                    if (configurator == null) {
                        if (problems == null) {
                            configuratorRef.set(configurator = new PropertyLogContextConfigurator());
                        } else {
                            final ServiceConfigurationError e = new ServiceConfigurationError(
                                    "Failed to configure log configurator service");
                            for (Throwable problem : problems) {
                                e.addSuppressed(problem);
                            }
                            throw e;
                        }
                    }
                }
            }
        }
        configurator.configure(LogContext.getSystemLogContext(), inputStream);
    }

    /**
     * Does nothing.
     *
     * @param mapper not used
     */
    public void updateConfiguration(final Function<String, BiFunction<String, String, String>> mapper) throws IOException {
        // no operation the configuration API should be used
    }

    /**
     * Does nothing.
     *
     * @param ins    not used
     * @param mapper not used
     */
    public void updateConfiguration(final InputStream ins, final Function<String, BiFunction<String, String, String>> mapper)
            throws IOException {
        // no operation the configuration API should be used
    }

    /**
     * Configuration listeners are not currently supported.
     *
     * @param listener not used
     *
     * @return this log manager
     */
    public java.util.logging.LogManager addConfigurationListener(final Runnable listener) {
        // no operation
        return this;
    }

    /**
     * Configuration listeners are not currently supported.
     *
     * @param listener not used
     */
    public void removeConfigurationListener(final Runnable listener) {
        // no operation
    }

    /**
     * Does nothing. Properties are not supported.
     *
     * @param name ignored
     * @return {@code null}
     */
    public String getProperty(String name) {
        // no properties
        return null;
    }

    /**
     * Does nothing. This method only causes trouble.
     */
    public void reset() {
        // no operation!
    }

    @Override
    public Enumeration<String> getLoggerNames() {
        return LogContext.getLogContext().getLoggerNames();
    }

    /**
     * Do nothing. Loggers are only added/acquired via {@link #getLogger(String)}.
     *
     * @param logger ignored
     * @return {@code false}
     */
    public boolean addLogger(java.util.logging.Logger logger) {
        return false;
    }

    /**
     * Get or create a logger with the given name.
     *
     * @param name the logger name
     * @return the corresponding logger
     */
    public Logger getLogger(String name) {
        return LogContext.getLogContext().getLogger(name);
    }

    /**
     * Returns the currently set filter for this thread or {@code null} if one has not been set.
     * <p>
     * If the {@link #PER_THREAD_LOG_FILTER_KEY} is not set to {@code true} then {@code null} will always be returned.
     * </p>
     *
     * @return the filter set for the thread or {@code null} if no level was set
     */
    public static Filter getThreadLocalLogFilter() {
        return PER_THREAD_LOG_FILTER ? LocalFilterHolder.LOCAL_FILTER.get() : null;
    }

    /**
     * Sets the filter on the thread for all loggers.
     * <p>
     * This feature only works if the {@link #PER_THREAD_LOG_FILTER} was set to {@code true}
     * </p>
     *
     * @param filter the filter to set for all loggers on this thread
     */
    public static void setThreadLocalLogLevel(final Filter filter) {
        if (PER_THREAD_LOG_FILTER) {
            LocalFilterHolder.LOCAL_FILTER.set(filter);
        }
    }
}
