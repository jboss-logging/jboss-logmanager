/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.Version;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class JDKSpecific {
    private JDKSpecific() {
    }

    private static final Gateway GATEWAY;
    private static final boolean JBOSS_MODULES;

    static {
        GATEWAY = AccessController.doPrivileged(new PrivilegedAction<Gateway>() {
            public Gateway run() {
                return new Gateway();
            }
        });
        boolean jbossModules = false;
        try {
            Module.getStartTime();
            jbossModules = true;
        } catch (Throwable ignored) {
        }
        JBOSS_MODULES = jbossModules;
    }

    static final class Gateway extends SecurityManager {
        protected Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }

    static Class<?> findCallingClass(Set<ClassLoader> rejectClassLoaders) {
        for (Class<?> caller : GATEWAY.getClassContext()) {
            final ClassLoader classLoader = caller.getClassLoader();
            if (classLoader != null && !rejectClassLoaders.contains(classLoader)) {
                return caller;
            }
        }
        return null;
    }

    static Collection<Class<?>> findCallingClasses(Set<ClassLoader> rejectClassLoaders) {
        final Collection<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> caller : GATEWAY.getClassContext()) {
            final ClassLoader classLoader = caller.getClassLoader();
            if (classLoader != null && !rejectClassLoaders.contains(classLoader)) {
                result.add(caller);
            }
        }
        return result;
    }

    static void calculateCaller(ExtLogRecord logRecord) {
        final String loggerClassName = logRecord.getLoggerClassName();
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        final Class<?>[] classes = GATEWAY.getClassContext();
        // The stack trace may be missing classes, but the class context is not, so if we find a mismatch, we skip the class context items.
        int i = 1, j = 0;
        Class<?> clazz = classes[i++];
        StackTraceElement element = stackTrace[j++];
        boolean found = false;
        for (; ; ) {
            if (clazz.getName().equals(element.getClassName())) {
                if (clazz.getName().equals(loggerClassName)) {
                    // next entry could be the one we want!
                    found = true;
                } else {
                    if (found) {
                        logRecord.setSourceClassName(element.getClassName());
                        logRecord.setSourceMethodName(element.getMethodName());
                        logRecord.setSourceFileName(element.getFileName());
                        logRecord.setSourceLineNumber(element.getLineNumber());
                        if (JBOSS_MODULES) {
                            calculateModule(logRecord, clazz);
                        } else {
                            // If JBoss Modules is not installed we want to defer to a possible ModuleResolver
                            // implementation. JBoss Modules itself may have it's own implementation for cases when
                            // the log manager is on the boot class path.
                            logRecord.setSourceModuleName(ModuleResolverFactory.getInstance().getModuleNameOf(clazz));
                            logRecord.setSourceModuleVersion(ModuleResolverFactory.getInstance().getModuleVersionOf(clazz));
                        }
                        return;
                    }
                }
                if (j == stackTrace.length) {
                    logRecord.setUnknownCaller();
                    return;
                }
                element = stackTrace[j++];
            }
            if (i == classes.length) {
                logRecord.setUnknownCaller();
                return;
            }
            clazz = classes[i++];
        }
    }

    static String getModuleNameOf(final Class<?> clazz) {
        if (JBOSS_MODULES) {
            final ClassLoader cl = clazz.getClassLoader();
            if (cl instanceof ModuleClassLoader) {
                return ((ModuleClassLoader) cl).getModule().getName();
            }
        }
        return null;
    }

    static String getModuleVersionOf(final Class<?> clazz) {
        if (JBOSS_MODULES) {
            final ClassLoader cl = clazz.getClassLoader();
            if (cl instanceof ModuleClassLoader) {
                final Version version = ((ModuleClassLoader) cl).getModule().getVersion();
                if (version != null) {
                    return version.toString();
                }
            }
        }
        return null;
    }

    private static void calculateModule(final ExtLogRecord logRecord, final Class<?> clazz) {
        final Module module = Module.forClass(clazz);
        if (module != null) {
            logRecord.setSourceModuleName(module.getName());
            final Version version = module.getVersion();
            if (version != null) {
                logRecord.setSourceModuleVersion(version.toString());
            } else {
                logRecord.setSourceModuleVersion(null);
            }
        }
    }
}
