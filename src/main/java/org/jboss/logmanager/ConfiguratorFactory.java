/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

/**
 * Used to create a {@link LogContextConfigurator}. The {@linkplain #priority() priority} is used to determine which
 * factory should be used. The lowest priority factory is used. If two factories have the same priority the second
 * factory will not be used. The order of loading the factories for determining priority is done via the
 * {@link java.util.ServiceLoader#load(Class, ClassLoader)}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ConfiguratorFactory {

    /**
     * Creates the {@link LogContextConfigurator}.
     *
     * @return the log context configurator
     */
    LogContextConfigurator create();

    /**
     * The priority for the factory which is used to determine which factory should be used to create a
     * {@link LogContextConfigurator}. The lowest priority factory will be used.
     *
     * @return the priority for this factory
     */
    int priority();
}
