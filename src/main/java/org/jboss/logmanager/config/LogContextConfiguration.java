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

import java.util.List;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.handlers.DelayedHandler;

/**
 * A log context configuration.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("unused")
public interface LogContextConfiguration {

    /**
     * Get the log context being configured by this configuration object.
     *
     * @return the log context
     */
    LogContext getLogContext();

    LoggerConfiguration addLoggerConfiguration(String loggerName);

    boolean removeLoggerConfiguration(String loggerName);

    LoggerConfiguration getLoggerConfiguration(String loggerName);

    List<String> getLoggerNames();

    /**
     * Add a handler configuration.
     *
     * @param moduleName the module name, or {@code null} to use the logmanager's class path
     * @param className the class name of the handler (must not be {@code null})
     * @param handlerName the name of the handler (must be unique within this configuration and not {@code null})
     * @param constructorProperties an optional list of constructor property names
     * @return the new handler configuration
     */
    HandlerConfiguration addHandlerConfiguration(String moduleName, String className, String handlerName, String... constructorProperties);

    /**
     * Remove a handler configuration.  Also removes handler from everything it was added to.
     *
     * @param handlerName the handler name to remove
     * @return {@code true} if the handler was removed, {@code false} if the handler didn't exist
     */
    boolean removeHandlerConfiguration(String handlerName);

    HandlerConfiguration getHandlerConfiguration(String handlerName);

    List<String> getHandlerNames();

    FormatterConfiguration addFormatterConfiguration(String moduleName, String className, String formatterName, String... constructorProperties);

    boolean removeFormatterConfiguration(String formatterName);

    FormatterConfiguration getFormatterConfiguration(String formatterName);

    List<String> getFormatterNames();

    FilterConfiguration addFilterConfiguration(String moduleName, String className, String filterName, String... constructorProperties);

    boolean removeFilterConfiguration(String filterName);

    FilterConfiguration getFilterConfiguration(String filterName);

    List<String> getFilterNames();

    ErrorManagerConfiguration addErrorManagerConfiguration(String moduleName, String className, String errorManagerName, String... constructorProperties);

    boolean removeErrorManagerConfiguration(String errorManagerName);

    ErrorManagerConfiguration getErrorManagerConfiguration(String errorManagerName);

    List<String> getErrorManagerNames();

    /**
     * Prepares the current changes. The changes are applied into the running logging configuration, but can be rolled
     * back using the {@link #forget()} method if {@link #commit()} has not been invoked.
     */
    void prepare();

    /**
     * Add a POJO configuration.
     *
     * @param moduleName            the module name, or {@code null} to use the logmanager's class path
     * @param className             the class name of the POJO (must not be {@code null})
     * @param pojoName              the name of the POJO (must be unique within this configuration and not {@code
     *                              null}
     * @param constructorProperties an optional list of constructor property names
     *
     * @return the new handler configuration
     */
    PojoConfiguration addPojoConfiguration(String moduleName, String className, String pojoName, String... constructorProperties);

    /**
     * Removes the POJO configuration.
     *
     * @param pojoName the name of the POJO
     *
     * @return {@code true} if the configuration was removed, othwerwise {@code false} if the configuration did not
     *         exist or was not remove.
     */
    boolean removePojoConfiguration(String pojoName);

    /**
     * Gets the POJO configuration.
     *
     * @param pojoName the name of the POJO
     *
     * @return the POJO configuration if found, otherwise {@code null}
     */
    PojoConfiguration getPojoConfiguration(String pojoName);

    /**
     * A list of the POJO configuration names.
     *
     * @return a list of the names
     */
    List<String> getPojoNames();

    /**
     * Commit the current changes into the running logging configuration.
     */
    void commit();

    /**
     * Clear all the current changes and restore this object to its original state.
     */
    void forget();

    /**
     * Activates any {@linkplain DelayedHandler#activate() handlers} that require activation. This should likely only
     * be invoked after a {@link #prepare()}, {@link #commit()} or {@link #forget()}.
     */
    default void activate() {
        // Do nothing by default for backwards compatibility
    }

    /**
     * The factory class for persistent configurations.
     */
    class Factory {

        private Factory() {
        }

        /**
         * Construct a new persistent configuration for a log context.
         *
         *
         * @param logContext the log context to configure
         * @return the new persistent configuration
         */
        public static LogContextConfiguration create(LogContext logContext) {
            return new LogContextConfigurationImpl(logContext);
        }
    }
}
