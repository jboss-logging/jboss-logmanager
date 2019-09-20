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

import static java.security.AccessController.doPrivileged;

import java.lang.module.ModuleDescriptor;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.Version;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class JDKSpecific {

    static final StackWalker WALKER = doPrivileged(new GetStackWalkerAction());

    private JDKSpecific() {}

    private static final boolean JBOSS_MODULES;

    static {
        boolean jbossModules = false;
        try {
            Module.getStartTime();
            jbossModules = true;
        } catch (Throwable ignored) {}
        JBOSS_MODULES = jbossModules;
    }

    static Class<?> findCallingClass(Set<ClassLoader> rejectClassLoaders) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return doPrivileged(new FindCallingClassAction(rejectClassLoaders));
        } else {
            return WALKER.walk(new FindFirstWalkFunction(rejectClassLoaders));
        }
    }

    static Collection<Class<?>> findCallingClasses(Set<ClassLoader> rejectClassLoaders) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return doPrivileged(new FindCallingClassesAction(rejectClassLoaders));
        } else {
            return WALKER.walk(new FindAllWalkFunction(rejectClassLoaders));
        }
    }

    static void calculateCaller(ExtLogRecord logRecord) {
        WALKER.walk(new CallerCalcFunction(logRecord));
    }

    static String getModuleNameOf(final Class<?> clazz) {
        if (JBOSS_MODULES) {
            final ClassLoader cl = clazz.getClassLoader();
            if (cl instanceof ModuleClassLoader) {
                return ((ModuleClassLoader) cl).getModule().getName();
            }
        }
        final java.lang.Module module = clazz.getModule();
        return module.isNamed() ? module.getName() : null;
    }

    static String getModuleVersionOf(final Class<?> clazz) {
        if (JBOSS_MODULES) {
            final ClassLoader cl = clazz.getClassLoader();
            if (cl instanceof ModuleClassLoader) {
                final Version version = ((ModuleClassLoader) cl).getModule().getVersion();
                return version == null ? null : version.toString();
            }
        }
        final java.lang.Module module = clazz.getModule();
        final ModuleDescriptor.Version version = module.isNamed() ? module.getDescriptor().version().orElse(null) : null;
        return version == null ? null : version.toString();
    }

    private static void calculateJdkModule(final ExtLogRecord logRecord, final Class<?> clazz) {
        // Default to the ModuleResolver instance to resolve these values.
        logRecord.setSourceModuleName(ModuleResolverFactory.getInstance().getModuleNameOf(clazz));
        logRecord.setSourceModuleVersion(ModuleResolverFactory.getInstance().getModuleVersionOf(clazz));
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
        } else {
            calculateJdkModule(logRecord, clazz);
        }
    }

    static final class CallerCalcFunction implements Function<Stream<StackWalker.StackFrame>, Void> {
        private final ExtLogRecord logRecord;

        CallerCalcFunction(final ExtLogRecord logRecord) {
            this.logRecord = logRecord;
        }

        public Void apply(final Stream<StackWalker.StackFrame> stream) {
            final String loggerClassName = logRecord.getLoggerClassName();
            final Iterator<StackWalker.StackFrame> iterator = stream.iterator();
            boolean found = false;
            while (iterator.hasNext()) {
                final StackWalker.StackFrame frame = iterator.next();
                final Class<?> clazz = frame.getDeclaringClass();
                if (clazz.getName().equals(loggerClassName)) {
                    // next entry could be the one we want!
                    found = true;
                } else if (found) {
                    logRecord.setSourceClassName(frame.getClassName());
                    logRecord.setSourceMethodName(frame.getMethodName());
                    logRecord.setSourceFileName(frame.getFileName());
                    logRecord.setSourceLineNumber(frame.getLineNumber());
                    if (JBOSS_MODULES) {
                        // If JBoss Modules is installed directly invoke retrieving the module name and version from
                        // JBoss Modules rather than the overhead of using the ModuleResolver.
                        calculateModule(logRecord, clazz);
                    } else {
                        calculateJdkModule(logRecord, clazz);
                    }
                    return null;
                }
            }
            logRecord.setUnknownCaller();
            return null;
        }
    }

    static final class GetStackWalkerAction implements PrivilegedAction<StackWalker> {
        GetStackWalkerAction() {}

        public StackWalker run() {
            return StackWalker.getInstance(EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE));
        }
    }

    static final class FindCallingClassAction implements PrivilegedAction<Class<?>> {
        private final Set<ClassLoader> rejectClassLoaders;

        FindCallingClassAction(final Set<ClassLoader> rejectClassLoaders) {
            this.rejectClassLoaders = rejectClassLoaders;
        }

        public Class<?> run() {
            return WALKER.walk(new FindFirstWalkFunction(rejectClassLoaders));
        }
    }

    static final class FindCallingClassesAction implements PrivilegedAction<Collection<Class<?>>> {
        private final Set<ClassLoader> rejectClassLoaders;

        FindCallingClassesAction(final Set<ClassLoader> rejectClassLoaders) {
            this.rejectClassLoaders = rejectClassLoaders;
        }

        public Collection<Class<?>> run() {
            return WALKER.walk(new FindAllWalkFunction(rejectClassLoaders));
        }
    }

    static final class FindFirstWalkFunction implements Function<Stream<StackWalker.StackFrame>, Class<?>> {
        private final Set<ClassLoader> rejectClassLoaders;

        FindFirstWalkFunction(final Set<ClassLoader> rejectClassLoaders) {
            this.rejectClassLoaders = rejectClassLoaders;
        }

        public Class<?> apply(final Stream<StackWalker.StackFrame> stream) {
            final Iterator<StackWalker.StackFrame> iterator = stream.iterator();
            while (iterator.hasNext()) {
                final Class<?> clazz = iterator.next().getDeclaringClass();
                final ClassLoader classLoader = clazz.getClassLoader();
                if (! rejectClassLoaders.contains(classLoader)) {
                    return clazz;
                }
            }
            return null;
        }
    }

    static final class FindAllWalkFunction implements Function<Stream<StackWalker.StackFrame>, Collection<Class<?>>> {
        private final Set<ClassLoader> rejectClassLoaders;

        FindAllWalkFunction(final Set<ClassLoader> rejectClassLoaders) {
            this.rejectClassLoaders = rejectClassLoaders;
        }

        public Collection<Class<?>> apply(final Stream<StackWalker.StackFrame> stream) {
            final Collection<Class<?>> results = new LinkedHashSet<>();
            final Iterator<StackWalker.StackFrame> iterator = stream.iterator();
            while (iterator.hasNext()) {
                final Class<?> clazz = iterator.next().getDeclaringClass();
                final ClassLoader classLoader = clazz.getClassLoader();
                if (classLoader != null && ! rejectClassLoaders.contains(classLoader)) {
                    results.add(clazz);
                }
            }
            return results;
        }
    }
}
