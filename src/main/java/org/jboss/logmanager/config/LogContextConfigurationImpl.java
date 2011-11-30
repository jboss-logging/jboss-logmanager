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

import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;

import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class LogContextConfigurationImpl implements LogContextConfiguration {

    private final LogContext logContext;

    private final Map<String, LoggerConfigurationImpl> loggers = new HashMap<String, LoggerConfigurationImpl>();
    private final Map<String, HandlerConfigurationImpl> handlers = new HashMap<String, HandlerConfigurationImpl>();
    private final Map<String, FormatterConfigurationImpl> formatters = new HashMap<String, FormatterConfigurationImpl>();
    private final Map<String, FilterConfigurationImpl> filters = new HashMap<String, FilterConfigurationImpl>();
    private final Map<String, ErrorManagerConfigurationImpl> errorManagers = new HashMap<String, ErrorManagerConfigurationImpl>();
    private final Map<String, Logger> loggerRefs = new HashMap<String, Logger>();
    private final Map<String, Handler> handlerRefs = new HashMap<String, Handler>();
    private final Map<String, Filter> filterRefs = new HashMap<String, Filter>();
    private final Map<String, Formatter> formatterRefs = new HashMap<String, Formatter>();
    private final Map<String, ErrorManager> errorManagerRefs = new HashMap<String, ErrorManager>();

    private final Deque<ConfigAction<?>> transactionState = new ArrayDeque<ConfigAction<?>>();

    LogContextConfigurationImpl(final LogContext logContext) {
        this.logContext = logContext;
    }

    public LogContext getLogContext() {
        return logContext;
    }

    public LoggerConfiguration addLoggerConfiguration(final String loggerName) {
        if (loggers.containsKey(loggerName)) {
            throw new IllegalArgumentException(String.format("Logger \"%s\" already exists", loggerName));
        }
        final LoggerConfigurationImpl loggerConfiguration = new LoggerConfigurationImpl(loggerName, this);
        loggers.put(loggerName, loggerConfiguration);
        transactionState.addLast(new ConfigAction<Logger>() {
            public Logger validate() throws IllegalArgumentException {
                return logContext.getLogger(loggerName);
            }

            public void applyPreCreate(final Logger param) {
                loggerRefs.put(loggerName, param);
            }

            public void applyPostCreate(Logger param) {
            }

            public void rollback() {
                loggers.remove(loggerName);
            }
        });
        return loggerConfiguration;
    }

    public boolean removeLoggerConfiguration(final String loggerName) {
        final LoggerConfigurationImpl removed = loggers.remove(loggerName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public LoggerConfiguration getLoggerConfiguration(final String loggerName) {
        return loggers.get(loggerName);
    }

    public List<String> getLoggerNames() {
        return Collections.unmodifiableList(new ArrayList<String>(loggers.keySet()));
    }

    public HandlerConfiguration addHandlerConfiguration(final String moduleName, final String className, final String handlerName, final String... constructorProperties) {
        if (handlers.containsKey(handlerName)) {
            throw new IllegalArgumentException(String.format("Handler \"%s\" already exists", handlerName));
        }
        final HandlerConfigurationImpl handlerConfiguration = new HandlerConfigurationImpl(this, handlerName, moduleName, className, constructorProperties);
        handlers.put(handlerName, handlerConfiguration);
        addAction(handlerConfiguration.getConstructAction());
        return handlerConfiguration;
    }

    public boolean removeHandlerConfiguration(final String handlerName) {
        final HandlerConfigurationImpl removed = handlers.remove(handlerName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public HandlerConfiguration getHandlerConfiguration(final String handlerName) {
        return handlers.get(handlerName);
    }

    public List<String> getHandlerNames() {
        return Collections.unmodifiableList(new ArrayList<String>(handlers.keySet()));
    }

    public FormatterConfiguration addFormatterConfiguration(final String moduleName, final String className, final String formatterName, final String... constructorProperties) {
        if (formatters.containsKey(formatterName)) {
            throw new IllegalArgumentException(String.format("Formatter \"%s\" already exists", formatterName));
        }
        final FormatterConfigurationImpl formatterConfiguration = new FormatterConfigurationImpl(this, formatterName, moduleName, className, constructorProperties);
        formatters.put(formatterName, formatterConfiguration);
        addAction(formatterConfiguration.getConstructAction());
        return formatterConfiguration;
    }

    public boolean removeFormatterConfiguration(final String formatterName) {
        final FormatterConfigurationImpl removed = formatters.remove(formatterName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public FormatterConfiguration getFormatterConfiguration(final String formatterName) {
        return formatters.get(formatterName);
    }

    public List<String> getFormatterNames() {
        return Collections.unmodifiableList(new ArrayList<String>(formatters.keySet()));
    }

    public FilterConfiguration addFilterConfiguration(final String moduleName, final String className, final String filterName, final String... constructorProperties) {
        if (filters.containsKey(filterName)) {
            throw new IllegalArgumentException(String.format("Filter \"%s\" already exists", filterName));
        }
        final FilterConfigurationImpl filterConfiguration = new FilterConfigurationImpl(this, filterName, moduleName, className, constructorProperties);
        filters.put(filterName, filterConfiguration);
        addAction(filterConfiguration.getConstructAction());
        return filterConfiguration;
    }

    public boolean removeFilterConfiguration(final String filterName) {
        final FilterConfigurationImpl removed = filters.remove(filterName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public FilterConfiguration getFilterConfiguration(final String filterName) {
        return filters.get(filterName);
    }

    public List<String> getFilterNames() {
        return Collections.unmodifiableList(new ArrayList<String>(filters.keySet()));
    }

    public ErrorManagerConfiguration addErrorManagerConfiguration(final String moduleName, final String className, final String errorManagerName, final String... constructorProperties) {
        if (errorManagers.containsKey(errorManagerName)) {
            throw new IllegalArgumentException(String.format("ErrorManager \"%s\" already exists", errorManagerName));
        }
        final ErrorManagerConfigurationImpl errorManagerConfiguration = new ErrorManagerConfigurationImpl(this, errorManagerName, moduleName, className, constructorProperties);
        errorManagers.put(errorManagerName, errorManagerConfiguration);
        addAction(errorManagerConfiguration.getConstructAction());
        return errorManagerConfiguration;
    }

    public boolean removeErrorManagerConfiguration(final String errorManagerName) {
        final ErrorManagerConfigurationImpl removed = errorManagers.remove(errorManagerName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public ErrorManagerConfiguration getErrorManagerConfiguration(final String errorManagerName) {
        return errorManagers.get(errorManagerName);
    }

    public List<String> getErrorManagerNames() {
        return Collections.unmodifiableList(new ArrayList<String>(errorManagers.keySet()));
    }

    public void commit() {
        List<Object> items = new ArrayList<Object>();
        for (ConfigAction<?> action : transactionState) {
            items.add(action.validate());
        }
        Iterator<Object> iterator = items.iterator();
        for (ConfigAction<?> action : transactionState) {
            doApplyPreCreate(action, iterator.next());
        }
        iterator = items.iterator();
        for (ConfigAction<?> action : transactionState) {
            doApplyPostCreate(action, iterator.next());
        }
        transactionState.clear();
    }

    @SuppressWarnings("unchecked")
    private static <T> void doApplyPreCreate(ConfigAction<T> action, Object arg) {
        try {
            action.applyPreCreate((T) arg);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static <T> void doApplyPostCreate(ConfigAction<T> action, Object arg) {
        try {
            action.applyPostCreate((T) arg);
        } catch (Throwable ignored) {}
    }

    public void forget() {
        final Iterator<ConfigAction<?>> iterator = transactionState.descendingIterator();
        while (iterator.hasNext()) {
            final ConfigAction<?> action = iterator.next();
            try {
                action.rollback();
            } catch (Throwable ignored) {
            }
        }
        transactionState.clear();
    }

    void addAction(final ConfigAction<?> action) {
        transactionState.addLast(action);
    }

    ObjectProducer getValue(final Class<?> objClass, final String propertyName, final Class<?> paramType, final String valueString, final boolean immediate) {
        if (paramType == String.class) {
            return new SimpleObjectProducer(valueString);
        } else if (paramType == Handler.class) {
            if (! handlers.containsKey(valueString) || immediate && ! handlerRefs.containsKey(valueString)) {
                throw new IllegalArgumentException(String.format("No handler named \"%s\" is defined", valueString));
            }
            if (immediate) {
                return new SimpleObjectProducer(handlerRefs.get(valueString));
            } else {
                return new RefProducer(valueString, handlerRefs);
            }
        } else if (paramType == Filter.class) {
            if (! filters.containsKey(valueString) || immediate && ! filterRefs.containsKey(valueString)) {
                throw new IllegalArgumentException(String.format("No filter named \"%s\" is defined", valueString));
            }
            if (immediate) {
                return new SimpleObjectProducer(filterRefs.get(valueString));
            } else {
                return new RefProducer(valueString, filterRefs);
            }
        } else if (paramType == Formatter.class) {
            if (! formatters.containsKey(valueString) || immediate && ! formatterRefs.containsKey(valueString)) {
                throw new IllegalArgumentException(String.format("No formatter named \"%s\" is defined", valueString));
            }
            if (immediate) {
                return new SimpleObjectProducer(formatterRefs.get(valueString));
            } else {
                return new RefProducer(valueString, formatterRefs);
            }
        } else if (paramType == ErrorManager.class) {
            if (! errorManagers.containsKey(valueString) || immediate && ! errorManagerRefs.containsKey(valueString)) {
                throw new IllegalArgumentException(String.format("No error manager named \"%s\" is defined", valueString));
            }
            if (immediate) {
                return new SimpleObjectProducer(errorManagerRefs.get(valueString));
            } else {
                return new RefProducer(valueString, errorManagerRefs);
            }
        } else if (paramType == java.util.logging.Level.class) {
            return new SimpleObjectProducer(LogContext.getSystemLogContext().getLevelForName(valueString));
        } else if (paramType == java.util.logging.Logger.class) {
            return new SimpleObjectProducer(LogContext.getSystemLogContext().getLogger(valueString));
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return new SimpleObjectProducer(Boolean.valueOf(valueString));
        } else if (paramType == byte.class || paramType == Byte.class) {
            return new SimpleObjectProducer(Byte.valueOf(valueString));
        } else if (paramType == short.class || paramType == Short.class) {
            return new SimpleObjectProducer(Short.valueOf(valueString));
        } else if (paramType == int.class || paramType == Integer.class) {
            return new SimpleObjectProducer(Integer.valueOf(valueString));
        } else if (paramType == long.class || paramType == Long.class) {
            return new SimpleObjectProducer(Long.valueOf(valueString));
        } else if (paramType == float.class || paramType == Float.class) {
            return new SimpleObjectProducer(Float.valueOf(valueString));
        } else if (paramType == double.class || paramType == Double.class) {
            return new SimpleObjectProducer(Double.valueOf(valueString));
        } else if (paramType == char.class || paramType == Character.class) {
            return new SimpleObjectProducer(Character.valueOf(valueString.length() > 0 ? valueString.charAt(0) : 0));
        } else if (paramType == TimeZone.class) {
            return new SimpleObjectProducer(TimeZone.getTimeZone(valueString));
        } else if (paramType == Charset.class) {
            return new SimpleObjectProducer(Charset.forName(valueString));
        } else if (paramType.isEnum()) {
            return new SimpleObjectProducer(Enum.valueOf(paramType.asSubclass(Enum.class), valueString));
        } else {
            throw new IllegalArgumentException("Unknown parameter type for property " + propertyName + " on " + objClass);
        }
    }

    Map<String, Filter> getFilterRefs() {
        return filterRefs;
    }

    Map<String, FilterConfigurationImpl> getFilterConfigurations() {
        return filters;
    }

    Map<String, ErrorManager> getErrorManagerRefs() {
        return errorManagerRefs;
    }

    Map<String, ErrorManagerConfigurationImpl> getErrorManagerConfigurations() {
        return errorManagers;
    }

    Map<String, Handler> getHandlerRefs() {
        return handlerRefs;
    }

    Map<String, HandlerConfigurationImpl> getHandlerConfigurations() {
        return handlers;
    }

    Map<String, Formatter> getFormatterRefs() {
        return formatterRefs;
    }

    Map<String, FormatterConfigurationImpl> getFormatterConfigurations() {
        return formatters;
    }

    Map<String, Logger> getLoggerRefs() {
        return loggerRefs;
    }

    Map<String, LoggerConfigurationImpl> getLoggerConfigurations() {
        return loggers;
    }
}
