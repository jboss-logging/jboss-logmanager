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
 * Represents a configuration resource. If the resource is a {@link AutoCloseable}, then invoking {@link #close()} on
 * this resource will close the resource.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ConfigurationResource<T> extends Supplier<T>, AutoCloseable {

    /**
     * Creates a configuration resource which lazily invokes the supplier. Note that {@link #close()} will only close
     * the resource if {@link #get()} was first invoked to retrieve the value from the supplier.
     *
     * @param supplier the supplier used to create the configuration resource
     * @return the configuration resource represented by a lazy instance
     */
    static <T> ConfigurationResource<T> of(final Supplier<T> supplier) {
        if (supplier instanceof ConfigurationResource) {
            return (ConfigurationResource<T>) supplier;
        }
        return new LazyConfigurationResource<>(supplier);
    }

    /**
     * Creates a configuration resource with the instance as a constant. Note that if {@link #close()} is invoked,
     * {@link #get()} will return {@code null}.
     *
     * @param instance the constant instance
     * @return the configuration resource represented by a constant instance
     */
    static <T> ConfigurationResource<T> of(final T instance) {
        return new ConstantConfigurationResource<>(instance);
    }
}
