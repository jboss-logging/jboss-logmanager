/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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
 * An interface which can be used to locate information about a module.
 *
 * <p>
 * Note that if JBoss Modules is found on the class path for the log manager the {@link #getModuleNameOf(Class)} and
 * {@link #getModuleVersionOf(Class)} will not be invoked on the resolver and the name and version will be resolved from
 * JBoss Modules itself.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ModuleResolver {

    /**
     * Retrieves the name of the module for the specified class.
     *
     * @param clazz the class to resolve the module for
     *
     * @return the name of the module for the class or {@code null} if it could not be determined
     */
    default String getModuleNameOf(Class<?> clazz) {
        return JDKSpecific.getModuleNameOf(clazz);
    }

    /**
     * Retrieves the version of the module for the specified class.
     *
     * @param clazz the class to resolve the module for
     *
     * @return the version of the module for the class or {@code null} if it could not be determined
     */
    default String getModuleVersionOf(Class<?> clazz) {
        return JDKSpecific.getModuleVersionOf(clazz);
    }

    /**
     * Determines the class loader for the module.
     *
     * @param name the name of the module
     *
     * @return the class loader for the module
     *
     * @throws IllegalArgumentException if an error occurs determining the class loader
     */
    ClassLoader getModuleClassLoader(String name) throws IllegalArgumentException;
}
