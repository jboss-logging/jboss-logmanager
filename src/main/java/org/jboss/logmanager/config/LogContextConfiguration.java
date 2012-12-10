/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat Inc., and individual contributors
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

package org.jboss.logmanager.config;

import java.util.List;
import org.jboss.logmanager.LogContext;

/**
 * A log context configuration.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
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
     * Commit the current changes into the running logging configuration.
     */
    void commit();

    /**
     * Clear all the current changes and restore this object to its original state.
     */
    void forget();

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
