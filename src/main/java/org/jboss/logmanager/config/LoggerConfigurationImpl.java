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

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.Logger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class LoggerConfigurationImpl extends AbstractBasicConfiguration<Logger, LoggerConfigurationImpl> implements LoggerConfiguration {
    private ValueExpression<String> filter;
    private ValueExpression<Boolean> useParentHandlers;
    private ValueExpression<String> level;
    private final List<String> handlerNames = new ArrayList<String>(0);

    LoggerConfigurationImpl(final String name, final LogContextConfigurationImpl configuration) {
        super(name, configuration, configuration.getLoggerRefs(), configuration.getLoggerConfigurations());
    }

    public String getFilter() {
        return getFilterValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getFilterValueExpression() {
        return filter == null ? ValueExpression.NULL_STRING_EXPRESSION : filter;
    }

    public void setFilter(final String filter) {
        setFilter(ValueExpression.STRING_RESOLVER.resolve(filter));
    }

    @Override
    public void setFilter(final String expression, final String value) {
        setFilter(new ValueExpressionImpl<String>(expression, value));
    }

    private void setFilter(final ValueExpression<String> valueExpression) {
        final ValueExpression<String> oldFilterName = this.filter;
        this.filter = valueExpression;
        final String filterName = valueExpression.getResolvedValue();
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<ObjectProducer>() {
            public ObjectProducer validate() throws IllegalArgumentException {
                return configuration.resolveFilter(filterName);
            }

            public void applyPreCreate(final ObjectProducer param) {
            }

            public void applyPostCreate(final ObjectProducer param) {
                configuration.getLoggerRefs().get(getName()).setFilter((Filter) param.getObject());
            }

            public void rollback() {
                filter = oldFilterName;
            }
        });
    }


    public Boolean getUseParentHandlers() {
        return getUseParentHandlersValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<Boolean> getUseParentHandlersValueExpression() {
        return useParentHandlers == null ? ValueExpression.NULL_BOOLEAN_EXPRESSION : useParentHandlers;
    }

    public void setUseParentHandlers(final Boolean useParentHandlers) {
        setUseParentHandlers(new ValueExpressionImpl<Boolean>(null, useParentHandlers));
    }

    @Override
    public void setUseParentHandlers(final String expression) {
        setUseParentHandlers(ValueExpression.BOOLEAN_RESOLVER.resolve(expression));
    }

    @Override
    public void setUseParentHandlers(final String expression, final Boolean value) {
        setUseParentHandlers(new ValueExpressionImpl<Boolean>(expression, value));
    }

    private void setUseParentHandlers(final ValueExpression<Boolean> valueExpression) {
        final ValueExpression<Boolean> oldUseParentHandlers = this.useParentHandlers;
        this.useParentHandlers = valueExpression;
        final Boolean useParentHandlers = valueExpression.getResolvedValue();
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                if (useParentHandlers != null)
                    configuration.getLoggerRefs().get(getName()).setUseParentHandlers(useParentHandlers.booleanValue());
            }

            public void rollback() {
                LoggerConfigurationImpl.this.useParentHandlers = oldUseParentHandlers;
            }
        });
    }

    public String getLevel() {
        return getLevelValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getLevelValueExpression() {
        return level == null ? ValueExpression.NULL_STRING_EXPRESSION : level;
    }

    public void setLevel(final String level) {
        setLevelValueExpression(ValueExpression.STRING_RESOLVER.resolve(level));
    }

    @Override
    public void setLevel(final String expression, final String level) {
        setLevelValueExpression(new ValueExpressionImpl<String>(expression, level));
    }

    private void setLevelValueExpression(final ValueExpression<String> expression) {
        final ValueExpression oldLevel = this.level;
        this.level = expression;
        final String resolvedLevel = expression.getResolvedValue();
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Level>() {
            public Level validate() throws IllegalArgumentException {
                return resolvedLevel == null ? null : configuration.getLogContext().getLevelForName(resolvedLevel);
            }

            public void applyPreCreate(final Level param) {
            }

            public void applyPostCreate(final Level param) {
                configuration.getLoggerRefs().get(getName()).setLevel(param);
            }

            public void rollback() {
                LoggerConfigurationImpl.this.level = oldLevel;
            }
        });
    }

    public List<String> getHandlerNames() {
        return new ArrayList<String>(handlerNames);
    }

    public void setHandlerNames(final String... names) {
        final String[] oldHandlerNames = handlerNames.toArray(new String[handlerNames.size()]);
        handlerNames.clear();
        final LinkedHashSet<String> strings = new LinkedHashSet<String>(asList(names));
        handlerNames.addAll(strings);
        final String[] stringsArray = strings.toArray(new String[strings.size()]);
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                for (String name : stringsArray) {
                    if (configuration.getHandlerConfiguration(name) == null) {
                        throw new IllegalArgumentException(String.format("Handler \"%s\" is not found", name));
                    }
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                final Map<String, Handler> handlerRefs = configuration.getHandlerRefs();
                final Map<String, Logger> loggerRefs = configuration.getLoggerRefs();
                final Logger logger = loggerRefs.get(getName());
                final int length = stringsArray.length;
                final Handler[] handlers = new Handler[length];
                for (int i = 0; i < length; i++) {
                    handlers[i] = handlerRefs.get(stringsArray[i]);
                }
                logger.setHandlers(handlers);
            }

            public void rollback() {
                handlerNames.clear();
                handlerNames.addAll(asList(oldHandlerNames));
            }
        });
    }

    public void setHandlerNames(final Collection<String> names) {
        setHandlerNames(names.toArray(new String[names.size()]));
    }

    public boolean addHandlerName(final String name) {
        final LogContextConfigurationImpl configuration = getConfiguration();
        if (handlerNames.contains(name)) {
            return false;
        }
        handlerNames.add(name);
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                if (configuration.getHandlerConfiguration(name) == null) {
                    throw new IllegalArgumentException(String.format("Handler \"%s\" is not found", name));
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                final Map<String, Handler> handlerRefs = configuration.getHandlerRefs();
                final Map<String, Logger> loggerRefs = configuration.getLoggerRefs();
                final Logger logger = loggerRefs.get(getName());
                logger.addHandler(handlerRefs.get(name));
            }

            public void rollback() {
                handlerNames.remove(name);
            }
        });
        return true;
    }

    public boolean removeHandlerName(final String name) {
        final LogContextConfigurationImpl configuration = getConfiguration();
        if (!handlerNames.contains(name)) {
            return false;
        }
        final int index = handlerNames.indexOf(name);
        handlerNames.remove(index);
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                final Map<String, Handler> handlerRefs = configuration.getHandlerRefs();
                final Map<String, Logger> loggerRefs = configuration.getLoggerRefs();
                final Logger logger = (Logger) loggerRefs.get(getName());
                logger.removeHandler(handlerRefs.get(name));
            }

            public void rollback() {
                handlerNames.add(index, name);
            }
        });
        return true;
    }

    @Override
    ConfigAction<Void> getRemoveAction() {
        final String name = getName();
        final Logger refLogger = refs.get(name);
        final Filter filter;
        final Handler[] handlers;
        final Level level;
        final boolean useParentHandlers;
        if (refLogger == null) {
            filter = null;
            handlers = null;
            level = null;
            useParentHandlers = true;
        } else {
            filter = refLogger.getFilter();
            handlers = refLogger.getHandlers();
            level = refLogger.getLevel();
            useParentHandlers = refLogger.getUseParentHandlers();
        }
        return new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
                refs.remove(name);
            }

            public void applyPostCreate(final Void param) {
                if (refLogger != null) {
                    refLogger.setFilter(null);
                    refLogger.clearHandlers();
                    refLogger.setLevel(null);
                    refLogger.setUseParentHandlers(true);
                }
            }

            @SuppressWarnings({"unchecked"})
            public void rollback() {
                if (refLogger != null) {
                    refLogger.setFilter(filter);
                    if (handlers != null) refLogger.setHandlers(handlers);
                    refLogger.setLevel(level);
                    refLogger.setUseParentHandlers(useParentHandlers);
                    configs.put(name, LoggerConfigurationImpl.this);
                }
                clearRemoved();
            }
        };
    }
}
