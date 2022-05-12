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

import java.util.function.Supplier;

/**
 * A configuration resource
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ConfigurationResource<T> implements Supplier<T>, AutoCloseable {
    private final Supplier<T> supplier;
    private volatile T instance;

    private ConfigurationResource(final Supplier<T> supplier) {
        this.supplier = supplier;
    }

    static <T> ConfigurationResource<T> of(final Supplier<T> supplier) {
        if (supplier instanceof ConfigurationResource) {
            return (ConfigurationResource<T>) supplier;
        }
        return new ConfigurationResource<>(supplier);
    }

    @Override
    public T get() {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    instance = supplier.get();
                }
            }
        }
        return instance;
    }

    @Override
    public void close() throws Exception {
        synchronized (this) {
            if (instance instanceof AutoCloseable) {
                ((AutoCloseable) instance).close();
            }
            instance = null;
        }
    }
}
