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

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jboss.logmanager.ExtHandler;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;

import static java.util.Arrays.asList;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HandlerConfigurationImpl extends AbstractPropertyConfiguration<Handler, HandlerConfigurationImpl> implements HandlerConfiguration {

    private final List<String> handlerNames = new ArrayList<String>(0);

    private ValueExpression<String> formatterName;
    private ValueExpression<String> level;
    private ValueExpression<String> filter;
    private ValueExpression<String> encoding;
    private ValueExpression<String> errorManagerName;

    HandlerConfigurationImpl(final LogContextConfigurationImpl configuration, final String name, final String moduleName, final String className, final String[] constructorProperties) {
        super(Handler.class, configuration, configuration.getHandlerRefs(), configuration.getHandlerConfigurations(), name, moduleName, className, constructorProperties);
    }

    public String getFormatterName() {
        return getFormatterNameValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getFormatterNameValueExpression() {
        return formatterName == null ? ValueExpression.NULL_STRING_EXPRESSION : formatterName;
    }

    public void setFormatterName(final String formatterName) {
        setFormatterName(ValueExpression.STRING_RESOLVER.resolve(formatterName));
    }

    @Override
    public void setFormatterName(final String expression, final String value) {
        setFormatterName(new ValueExpressionImpl<String>(expression, value));
    }

    private void setFormatterName(final ValueExpression<String> expression) {
        final ValueExpression<String> oldFormatterName = this.formatterName;
        this.formatterName = expression;
        final String formatterName = expression.getResolvedValue();
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                if (formatterName != null && configuration.getFormatterConfiguration(formatterName) == null) {
                    throw new IllegalArgumentException(String.format("Formatter \"%s\" is not found", formatterName));
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                configuration.getHandlerRefs().get(getName()).setFormatter(formatterName == null ? null : configuration.getFormatterRefs().get(formatterName));
            }

            public void rollback() {
                HandlerConfigurationImpl.this.formatterName = oldFormatterName;
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
        final ValueExpression<String> oldLevel = this.level;
        this.level = expression;
        final String resolvedLevel = expression.getResolvedValue();
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Level>() {
            public Level validate() throws IllegalArgumentException {
                return resolvedLevel == null ? null : configuration.getLogContext().getLevelForName(resolvedLevel);
            }

            public void applyPreCreate(final Level param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Level param) {
                configuration.getHandlerRefs().get(getName()).setLevel(param);
            }

            public void rollback() {
                HandlerConfigurationImpl.this.level = oldLevel;
            }
        });
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

    private void setFilter(final ValueExpression<String> expression) {
        final ValueExpression<String> oldFilterName = this.filter;
        this.filter = expression;
        final String filterName = expression.getResolvedValue();
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<ObjectProducer>() {
            public ObjectProducer validate() throws IllegalArgumentException {
                return configuration.resolveFilter(filterName);
            }

            public void applyPreCreate(final ObjectProducer param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final ObjectProducer param) {
                configuration.getHandlerRefs().get(getName()).setFilter((Filter) param.getObject());
            }

            public void rollback() {
                HandlerConfigurationImpl.this.filter = oldFilterName;
            }
        });
    }

    public String getEncoding() {
        return getEncodingValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getEncodingValueExpression() {
        return encoding == null ? ValueExpression.NULL_STRING_EXPRESSION : encoding;
    }

    public void setEncoding(final String encoding) {
        setEncoding(ValueExpression.STRING_RESOLVER.resolve(encoding));
    }

    @Override
    public void setEncoding(final String expression, final String value) {
        setEncoding(new ValueExpressionImpl<String>(expression, value));
    }

    private void setEncoding(final ValueExpression<String> expression) {
        final ValueExpression<String> oldEncoding = this.encoding;
        this.encoding = expression;
        final String encoding = expression.getResolvedValue();
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                if (encoding != null) {
                    try {
                        Charset.forName(encoding);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(String.format("Unsupported character set \"%s\"", encoding));
                    }
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                try {
                    configuration.getHandlerRefs().get(getName()).setEncoding(encoding);
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException(String.format("The encoding value '%s' is invalid.", encoding), e);
                }
            }

            public void rollback() {
                HandlerConfigurationImpl.this.encoding = oldEncoding;
            }
        });
    }

    public String getErrorManagerName() {
        return getErrorManagerNameValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getErrorManagerNameValueExpression() {
        return errorManagerName == null ? ValueExpression.NULL_STRING_EXPRESSION : errorManagerName;
    }

    public void setErrorManagerName(final String errorManagerName) {
        setErrorManagerName(ValueExpression.STRING_RESOLVER.resolve(errorManagerName));
    }

    @Override
    public void setErrorManagerName(final String expression, final String value) {
        setErrorManagerName(new ValueExpressionImpl<String>(expression, value));
    }

    private void setErrorManagerName(final ValueExpression<String> expression) {
        final ValueExpression<String> oldErrorManagerName = this.errorManagerName;
        this.errorManagerName = expression;
        final String errorManagerName = expression.getResolvedValue();
        final LogContextConfigurationImpl configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                if (errorManagerName != null && configuration.getErrorManagerConfiguration(errorManagerName) == null) {
                    throw new IllegalArgumentException(String.format("errorManager \"%s\" is not found", errorManagerName));
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                configuration.getHandlerRefs().get(getName()).setErrorManager(errorManagerName == null ? null : configuration.getErrorManagerRefs().get(errorManagerName));
            }

            public void rollback() {
                HandlerConfigurationImpl.this.errorManagerName = oldErrorManagerName;
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
        if (! ExtHandler.class.isAssignableFrom(getActualClass())) {
            if (names.length == 0) {
                return;
            }
            throw new IllegalArgumentException("Nested handlers not supported for handler " + getActualClass());
        }
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
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                final Map<String, Handler> handlerRefs = configuration.getHandlerRefs();
                final ExtHandler handler = (ExtHandler) handlerRefs.get(getName());
                final int length = stringsArray.length;
                final Handler[] handlers = new Handler[length];
                for (int i = 0; i < length; i ++) {
                    handlers[i] = handlerRefs.get(stringsArray[i]);
                }
                handler.setHandlers(handlers);
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
        if (! ExtHandler.class.isAssignableFrom(getActualClass())) {
            throw new IllegalArgumentException("Nested handlers not supported for handler " + getActualClass());
        }
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
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                final Map<String, Handler> handlerRefs = configuration.getHandlerRefs();
                final ExtHandler handler = (ExtHandler) handlerRefs.get(getName());
                handler.addHandler(handlerRefs.get(name));
            }

            public void rollback() {
                handlerNames.remove(name);
            }
        });
        return true;
    }

    public boolean removeHandlerName(final String name) {
        final LogContextConfigurationImpl configuration = getConfiguration();
        if (! ExtHandler.class.isAssignableFrom(getActualClass())) {
            return false;
        }
        if (! handlerNames.contains(name)) {
            return false;
        }
        final int index = handlerNames.indexOf(name);
        handlerNames.remove(index);
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                final Map<String, Handler> handlerRefs = configuration.getHandlerRefs();
                final ExtHandler handler = (ExtHandler) handlerRefs.get(getName());
                handler.removeHandler(handlerRefs.get(name));
            }

            public void rollback() {
                handlerNames.add(index, name);
            }
        });
        return true;
    }

    String getDescription() {
        return "handler";
    }

    @Override
    ConfigAction<Handler> getConstructAction() {
        return new ConstructAction() {
            @Override
            public void rollback() {
                final Handler handler = refs.remove(getName());
                if (handler != null) {
                    try {
                        handler.close();
                    } catch (Exception ignore) {
                    }
                }
                super.rollback();
            }
        };
    }


    @Override
    ConfigAction<Void> getRemoveAction() {
        return new ConfigAction<Void>() {
            @Override
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            @Override
            public void applyPreCreate(final Void param) {
                final Handler handler = refs.remove(getName());
                if (handler != null) {
                    handler.close();
                }
            }

            @Override
            public void applyPostCreate(final Void param) {
                removePostConfigurationActions();
            }

            @Override
            public void rollback() {
                configs.put(getName(), HandlerConfigurationImpl.this);
                clearRemoved();
                addPostConfigurationActions();
            }
        };
    }
}
