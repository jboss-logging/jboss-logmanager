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

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * The factory used to get an instance of the {@linkplain ModuleResolver module resolver}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ModuleResolverFactory {
    private static volatile ModuleResolver INSTANCE;

    /**
     * Returns the current instance of the {@link ModuleResolver}.
     *
     * @return the module resolver used to load modules
     */
    @SuppressWarnings("Convert2Lambda")
    public static ModuleResolver getInstance() {
        ModuleResolver result = INSTANCE;
        if (INSTANCE == null) {
            synchronized (ModuleResolverFactory.class) {
                if (INSTANCE == null) {
                    result = INSTANCE = new ModuleResolver() {
                        @Override
                        public ClassLoader getModuleClassLoader(final String name) throws IllegalArgumentException {
                            // If we're in a module use that loader, otherwise use the boot module loader
                            ModuleLoader moduleLoader = ModuleLoader.forClass(ModuleResolver.class);
                            if (moduleLoader == null) {
                                moduleLoader = Module.getBootModuleLoader();
                            }
                            try {
                                return moduleLoader.loadModule(name).getClassLoader();
                            } catch (ModuleLoadException e) {
                                throw new IllegalArgumentException("Could load module " + name, e);
                            }
                        }
                    };
                }
            }
        }
        return result;
    }

    /**
     * Sets the module resolver to use for loading modules. If set to {@code null} a default module resolver will be
     * used based on JBoss Modules.
     *
     * @param resolver the module resolver to use or {@code null} to use a default module resolver
     */
    @SuppressWarnings("unused")
    public static void setModuleResolver(final ModuleResolver resolver) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(LogContext.CONTROL_PERMISSION);
        }
        INSTANCE = resolver;
    }
}
