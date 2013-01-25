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
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.filters.AcceptAllFilter;
import org.jboss.logmanager.filters.AllFilter;
import org.jboss.logmanager.filters.AnyFilter;
import org.jboss.logmanager.filters.DenyAllFilter;
import org.jboss.logmanager.filters.InvertFilter;
import org.jboss.logmanager.filters.LevelChangingFilter;
import org.jboss.logmanager.filters.LevelFilter;
import org.jboss.logmanager.filters.LevelRangeFilter;
import org.jboss.logmanager.filters.RegexFilter;
import org.jboss.logmanager.filters.SubstituteFilter;

import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;

import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Character.isJavaIdentifierStart;
import static java.lang.Character.isWhitespace;

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
    private final Map<String, PojoConfigurationImpl> pojos = new HashMap<String, PojoConfigurationImpl>();
    private final Map<String, Logger> loggerRefs = new HashMap<String, Logger>();
    private final Map<String, Handler> handlerRefs = new HashMap<String, Handler>();
    private final Map<String, Filter> filterRefs = new HashMap<String, Filter>();
    private final Map<String, Formatter> formatterRefs = new HashMap<String, Formatter>();
    private final Map<String, ErrorManager> errorManagerRefs = new HashMap<String, ErrorManager>();
    private final Map<String, Object> pojoRefs = new HashMap<String, Object>();

    private final Deque<ConfigAction<?>> transactionState = new ArrayDeque<ConfigAction<?>>();
    private final Map<String, Deque<ConfigAction<?>>> postConfigurationTransactionState = new LinkedHashMap<String, Deque<ConfigAction<?>>>();

    private boolean prepared = false;

    private static final ObjectProducer ACCEPT_PRODUCER = new SimpleObjectProducer(AcceptAllFilter.getInstance());
    private static final ObjectProducer DENY_PRODUCER = new SimpleObjectProducer(DenyAllFilter.getInstance());

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
        return new ArrayList<String>(loggers.keySet());
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
        return new ArrayList<String>(handlers.keySet());
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
        return new ArrayList<String>(formatters.keySet());
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
        return new ArrayList<String>(filters.keySet());
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
        return new ArrayList<String>(errorManagers.keySet());
    }

    @Override
    public PojoConfiguration addPojoConfiguration(final String moduleName, final String className, final String pojoName, final String... constructorProperties) {
        if (pojos.containsKey(pojoName)) {
            throw new IllegalArgumentException(String.format("POJO \"%s\" already exists", pojoName));
        }
        final PojoConfigurationImpl pojoConfiguration = new PojoConfigurationImpl(this, pojoName, moduleName, className, constructorProperties);
        pojos.put(pojoName, pojoConfiguration);
        transactionState.addFirst(pojoConfiguration.getConstructAction());
        return pojoConfiguration;
    }

    @Override
    public boolean removePojoConfiguration(final String pojoName) {
        final PojoConfigurationImpl removed = pojos.remove(pojoName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        }
        return false;
    }

    @Override
    public PojoConfiguration getPojoConfiguration(final String pojoName) {
        return pojos.get(pojoName);
    }

    @Override
    public List<String> getPojoNames() {
        return new ArrayList<String>(pojos.keySet());
    }

    @Override
    public void prepare() {
        doPrepare(transactionState);
        for (Deque<ConfigAction<?>> items : postConfigurationTransactionState.values()) {
            doPrepare(items);
        }
        prepared = true;
    }

    public void commit() {
        if (!prepared) {
            prepare();
        }
        prepared = false;
        postConfigurationTransactionState.clear();
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
        doForget(transactionState);
        for (Deque<ConfigAction<?>> items : postConfigurationTransactionState.values()) {
            doForget(items);
        }
        prepared = false;
        postConfigurationTransactionState.clear();
        transactionState.clear();
    }

    private void doPrepare(final Deque<ConfigAction<?>> transactionState) {
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
    }

    private void doForget(final Deque<ConfigAction<?>> transactionState) {
        Iterator<ConfigAction<?>> iterator = transactionState.descendingIterator();
        while (iterator.hasNext()) {
            final ConfigAction<?> action = iterator.next();
            try {
                action.rollback();
            } catch (Throwable ignored) {
            }
        }
    }

    void addAction(final ConfigAction<?> action) {
        transactionState.addLast(action);
    }

    /**
     * Adds or replaces the post configuration actions for the configuration identified by the {@code name} parameter.
     *
     * @param name    the name of the configuration
     * @param actions the actions to be invoked after the properties have been set
     */
    void addPostConfigurationActions(final String name, final Deque<ConfigAction<?>> actions) {
        if (actions != null && !actions.isEmpty()) {
            postConfigurationTransactionState.put(name, actions);
        }
    }

    /**
     * Checks to see if configuration actions have already been defined for the configuration.
     * @param name the name of the configuration
     * @return {@code true} if the configuration actions have been defined, otherwise {@code false}
     */
    boolean postConfigurationActionsExist(final String name) {
        return postConfigurationTransactionState.containsKey(name);
    }

    ObjectProducer getValue(final Class<?> objClass, final String propertyName, final Class<?> paramType, final ValueExpression<String> valueExpression, final boolean immediate) {
        if (valueExpression == null || valueExpression.getResolvedValue() == null) {
            if (paramType.isPrimitive()) {
                throw new IllegalArgumentException(String.format("Cannot assign null value to primitive property \"%s\" of %s", propertyName, objClass));
            }
            return ObjectProducer.NULL_PRODUCER;
        }
        final String replaced = valueExpression.getResolvedValue();
        if (paramType == String.class) {
            return new SimpleObjectProducer(replaced);
        } else if (paramType == Handler.class) {
            if (! handlers.containsKey(replaced) || immediate && ! handlerRefs.containsKey(replaced)) {
                throw new IllegalArgumentException(String.format("No handler named \"%s\" is defined", replaced));
            }
            if (immediate) {
                return new SimpleObjectProducer(handlerRefs.get(replaced));
            } else {
                return new RefProducer(replaced, handlerRefs);
            }
        } else if (paramType == Filter.class) {
            return resolveFilter(replaced, immediate);
        } else if (paramType == Formatter.class) {
            if (! formatters.containsKey(replaced) || immediate && ! formatterRefs.containsKey(replaced)) {
                throw new IllegalArgumentException(String.format("No formatter named \"%s\" is defined", replaced));
            }
            if (immediate) {
                return new SimpleObjectProducer(formatterRefs.get(replaced));
            } else {
                return new RefProducer(replaced, formatterRefs);
            }
        } else if (paramType == ErrorManager.class) {
            if (! errorManagers.containsKey(replaced) || immediate && ! errorManagerRefs.containsKey(replaced)) {
                throw new IllegalArgumentException(String.format("No error manager named \"%s\" is defined", replaced));
            }
            if (immediate) {
                return new SimpleObjectProducer(errorManagerRefs.get(replaced));
            } else {
                return new RefProducer(replaced, errorManagerRefs);
            }
        } else if (paramType == java.util.logging.Level.class) {
            return new SimpleObjectProducer(LogContext.getSystemLogContext().getLevelForName(replaced));
        } else if (paramType == java.util.logging.Logger.class) {
            return new SimpleObjectProducer(LogContext.getSystemLogContext().getLogger(replaced));
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return new SimpleObjectProducer(Boolean.valueOf(replaced));
        } else if (paramType == byte.class || paramType == Byte.class) {
            return new SimpleObjectProducer(Byte.valueOf(replaced));
        } else if (paramType == short.class || paramType == Short.class) {
            return new SimpleObjectProducer(Short.valueOf(replaced));
        } else if (paramType == int.class || paramType == Integer.class) {
            return new SimpleObjectProducer(Integer.valueOf(replaced));
        } else if (paramType == long.class || paramType == Long.class) {
            return new SimpleObjectProducer(Long.valueOf(replaced));
        } else if (paramType == float.class || paramType == Float.class) {
            return new SimpleObjectProducer(Float.valueOf(replaced));
        } else if (paramType == double.class || paramType == Double.class) {
            return new SimpleObjectProducer(Double.valueOf(replaced));
        } else if (paramType == char.class || paramType == Character.class) {
            return new SimpleObjectProducer(Character.valueOf(replaced.length() > 0 ? replaced.charAt(0) : 0));
        } else if (paramType == TimeZone.class) {
            return new SimpleObjectProducer(TimeZone.getTimeZone(replaced));
        } else if (paramType == Charset.class) {
            return new SimpleObjectProducer(Charset.forName(replaced));
        } else if (paramType.isEnum()) {
            return new SimpleObjectProducer(Enum.valueOf(paramType.asSubclass(Enum.class), replaced));
        } else if (pojos.containsKey(replaced)) {
            return new RefProducer(replaced, pojoRefs);
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

    Map<String, Object> getPojoRefs() {
        return pojoRefs;
    }

    Map<String, PojoConfigurationImpl> getPojoConfigurations() {
        return pojos;
    }

    private static List<String> tokens(String source) {
        final List<String> tokens = new ArrayList<String>();
        final int length = source.length();
        int idx = 0;
        while (idx < length) {
            int ch;
            ch = source.codePointAt(idx);
            if (isWhitespace(ch)) {
                ch = source.codePointAt(idx);
                idx = source.offsetByCodePoints(idx, 1);
            } else if (isJavaIdentifierStart(ch)) {
                int start = idx;
                do {
                    idx = source.offsetByCodePoints(idx, 1);
                } while (idx < length && isJavaIdentifierPart(ch = source.codePointAt(idx)));
                tokens.add(source.substring(start, idx));
            } else if (ch == '"') {
                final StringBuilder b = new StringBuilder();
                // tag token as a string
                b.append('"');
                idx = source.offsetByCodePoints(idx, 1);
                while (idx < length && (ch = source.codePointAt(idx)) != '"') {
                    ch = source.codePointAt(idx);
                    if (ch == '\\') {
                        idx = source.offsetByCodePoints(idx, 1);
                        if (idx == length) {
                            throw new IllegalArgumentException("Truncated filter expression string");
                        }
                        ch = source.codePointAt(idx);
                        switch (ch) {
                            case '\\': b.append('\\'); break;
                            case '\'': b.append('\''); break;
                            case '"': b.append('"'); break;
                            case 'b': b.append('\b'); break;
                            case 'f': b.append('\f'); break;
                            case 'n': b.append('\n'); break;
                            case 'r': b.append('\r'); break;
                            case 't': b.append('\t'); break;
                            default:
                                throw new IllegalArgumentException("Invalid escape found in filter expression string");
                        }
                    } else {
                        b.appendCodePoint(ch);
                    }
                    idx = source.offsetByCodePoints(idx, 1);
                }
                idx = source.offsetByCodePoints(idx, 1);
                tokens.add(b.toString());
            } else {
                int start = idx;
                idx = source.offsetByCodePoints(idx, 1);
                tokens.add(source.substring(start, idx));
            }
        }
        return tokens;
    }

    private ObjectProducer parseFilterExpression(Iterator<String> iterator, boolean outermost, final boolean immediate) {
        if (! iterator.hasNext()) {
            if (outermost) {
                return ObjectProducer.NULL_PRODUCER;
            }
            throw endOfExpression();
        }
        final String token = iterator.next();
        if ("accept".equals(token)) {
            return ACCEPT_PRODUCER;
        } else if ("deny".equals(token)) {
            return DENY_PRODUCER;
        } else if ("not".equals(token)) {
            expect("(", iterator);
            final ObjectProducer nested = parseFilterExpression(iterator, false, immediate);
            expect(")", iterator);
            return new ObjectProducer() {
                public Object getObject() {
                    return new InvertFilter((Filter) nested.getObject());
                }
            };
        } else if ("all".equals(token)) {
            expect("(", iterator);
            final List<ObjectProducer> producers = new ArrayList<ObjectProducer>();
            do {
                producers.add(parseFilterExpression(iterator, false, immediate));
            } while (expect(",", ")", iterator));
            return new ObjectProducer() {
                public Object getObject() {
                    final int length = producers.size();
                    final Filter[] filters = new Filter[length];
                    for (int i = 0; i < length; i ++) {
                        filters[i] = (Filter) producers.get(i).getObject();
                    }
                    return new AllFilter(filters);
                }
            };
        } else if ("any".equals(token)) {
            expect("(", iterator);
            final List<ObjectProducer> producers = new ArrayList<ObjectProducer>();
            do {
                producers.add(parseFilterExpression(iterator, false, immediate));
            } while (expect(",", ")", iterator));
            return new ObjectProducer() {
                public Object getObject() {
                    final int length = producers.size();
                    final Filter[] filters = new Filter[length];
                    for (int i = 0; i < length; i ++) {
                        filters[i] = (Filter) producers.get(i).getObject();
                    }
                    return new AnyFilter(filters);
                }
            };
        } else if ("levelChange".equals(token)) {
            expect("(", iterator);
            final String levelName = expectName(iterator);
            final Level level = logContext.getLevelForName(levelName);
            expect(")", iterator);
            return new SimpleObjectProducer(new LevelChangingFilter(level));
        } else if ("levels".equals(token)) {
            expect("(", iterator);
            final Set<Level> levels = new HashSet<Level>();
            do {
                levels.add(logContext.getLevelForName(expectName(iterator)));
            } while (expect(",", ")", iterator));
            return new SimpleObjectProducer(new LevelFilter(levels));
        } else if ("levelRange".equals(token)) {
            final boolean minInclusive = expect("[", "(", iterator);
            final Level minLevel = logContext.getLevelForName(expectName(iterator));
            expect(",", iterator);
            final Level maxLevel = logContext.getLevelForName(expectName(iterator));
            final boolean maxInclusive = expect("]", ")", iterator);
            return new SimpleObjectProducer(new LevelRangeFilter(minLevel, minInclusive, maxLevel, maxInclusive));
        } else if ("match".equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(")", iterator);
            return new SimpleObjectProducer(new RegexFilter(pattern));
        } else if ("substitute".equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(",", iterator);
            final String replacement = expectString(iterator);
            expect(")", iterator);
            return new SimpleObjectProducer(new SubstituteFilter(pattern, replacement, false));
        } else if ("substituteAll".equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(",", iterator);
            final String replacement = expectString(iterator);
            expect(")", iterator);
            return new SimpleObjectProducer(new SubstituteFilter(pattern, replacement, true));
        } else {
            final String name = expectName(iterator);
            if (! filters.containsKey(name) || immediate && ! filterRefs.containsKey(name)) {
                throw new IllegalArgumentException(String.format("No filter named \"%s\" is defined", name));
            }
            if (immediate) {
                return new SimpleObjectProducer(filterRefs.get(name));
            } else {
                return new RefProducer(name, filterRefs);
            }
        }
    }

    private static String expectName(Iterator<String> iterator) {
        if (iterator.hasNext()) {
            final String next = iterator.next();
            if (isJavaIdentifierStart(next.codePointAt(0))) {
                return next;
            }
        }
        throw new IllegalArgumentException("Expected identifier next in filter expression");
    }

    private static String expectString(Iterator<String> iterator) {
        if (iterator.hasNext()) {
            final String next = iterator.next();
            if (next.codePointAt(0) == '"') {
                return next.substring(1);
            }
        }
        throw new IllegalArgumentException("Expected string next in filter expression");
    }

    private static boolean expect(String trueToken, String falseToken, Iterator<String> iterator) {
        final boolean hasNext = iterator.hasNext();
        final String next = hasNext ? iterator.next() : null;
        final boolean result;
        if (! hasNext || ! ((result = trueToken.equals(next)) || falseToken.equals(next))) {
            throw new IllegalArgumentException("Expected '" + trueToken + "' or '" + falseToken + "' next in filter expression");
        }
        return result;
    }

    private static void expect(String token, Iterator<String> iterator) {
        if (! iterator.hasNext() || ! token.equals(iterator.next())) {
            throw new IllegalArgumentException("Expected '" + token + "' next in filter expression");
        }
    }

    private static IllegalArgumentException endOfExpression() {
        return new IllegalArgumentException("Unexpected end of filter expression");
    }

    private ObjectProducer resolveFilter(String expression, final boolean immediate) {
        if (expression == null) {
            return ObjectProducer.NULL_PRODUCER;
        }
        // First check for a defined filter
        if (filters.containsKey(expression)) {
            if (immediate) {
                return new SimpleObjectProducer(filterRefs.get(expression));
            } else {
                return new RefProducer(expression, filterRefs);
            }
        }
        final Iterator<String> iterator = tokens(expression).iterator();
        final ObjectProducer result = parseFilterExpression(iterator, true, immediate);
        if (iterator.hasNext()) {
            throw new IllegalArgumentException("Extra data after filter expression");
        }
        return result;
    }

    ObjectProducer resolveFilter(String expression) {
        return resolveFilter(expression, false);
    }
}
