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
 * The factory used to get an instance of the {@linkplain ModuleFinder module finder}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ModuleFinderFactory {
    private static volatile ModuleFinder INSTANCE;

    /**
     * Returns the current instance of the {@link ModuleFinder}.
     *
     * @return the module finder used to load modules
     */
    @SuppressWarnings("Convert2Lambda")
    public static ModuleFinder getInstance() {
        ModuleFinder result = INSTANCE;
        if (INSTANCE == null) {
            synchronized (ModuleFinderFactory.class) {
                if (INSTANCE == null) {
                    result = INSTANCE = new ModuleFinder() {
                        @Override
                        public ClassLoader resolve(final String name) throws IllegalArgumentException {
                            // If we're in a module use that loader, otherwise use the boot module loader
                            ModuleLoader moduleLoader = ModuleLoader.forClass(ModuleFinder.class);
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
     * Sets the module finder to use for loading modules. If set to {@code null} a default module finder will be used
     * based on JBoss Modules.
     *
     * @param moduleFinder the module finder to use or {@code null} to use a default module finder
     */
    @SuppressWarnings("unused")
    public static void setModuleFinder(final ModuleFinder moduleFinder) {
        INSTANCE = moduleFinder;
    }
}
