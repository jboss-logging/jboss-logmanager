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

import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractBasicConfiguration<T, C extends AbstractBasicConfiguration<T, C>> implements NamedConfigurable {

    private final LogContextConfigurationImpl configuration;
    private final String name;
    private boolean removed;
    protected final Map<String, T> refs;
    protected final Map<String, C> configs;

    AbstractBasicConfiguration(final String name, final LogContextConfigurationImpl configuration, final Map<String, T> refs, final Map<String, C> configs) {
        this.name = name;
        this.configuration = configuration;
        this.refs = refs;
        this.configs = configs;
    }

    public String getName() {
        return name;
    }

    void clearRemoved() {
        removed = false;
    }

    void setRemoved() {
        removed = true;
    }

    boolean isRemoved() {
        return removed;
    }

    LogContextConfigurationImpl getConfiguration() {
        return configuration;
    }

    ConfigAction<Void> getRemoveAction() {
        return new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
                refs.remove(name);
            }

            public void applyPostCreate(final Void param) {
            }

            @SuppressWarnings({ "unchecked" })
            public void rollback() {
                configs.put(name, (C) AbstractBasicConfiguration.this);
                clearRemoved();
            }
        };
    }

    Map<String, T> getRefs() {
        return refs;
    }

    Map<String, C> getConfigs() {
        return configs;
    }
}
