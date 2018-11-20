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
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A log context selector which chooses a log context based on the caller's classloader. The first caller that is not
 * a {@linkplain #addLogApiClassLoader(ClassLoader) log API} or does not have a {@code null} classloader will be the
 * class loader used.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public final class CallerClassLoaderLogContextSelector implements LogContextSelector {

    private static final Permission REGISTER_LOG_CONTEXT_PERMISSION = new RuntimePermission("registerLogContext", null);
    private static final Permission UNREGISTER_LOG_CONTEXT_PERMISSION = new RuntimePermission("unregisterLogContext", null);
    private static final Permission LOG_API_PERMISSION = new RuntimePermission("logApiPermission", null);

    /**
     * Construct a new instance.  If no matching log context is found, the provided default selector is consulted.
     *
     * @param defaultSelector the selector to consult if no matching log context is found
     */
    public CallerClassLoaderLogContextSelector(final LogContextSelector defaultSelector) {
        this(defaultSelector, false);
    }

    /**
     * Construct a new instance.  If no matching log context is found, the provided default selector is consulted.
     * <p>
     * If the {@code checkParentClassLoaders} is set to {@code true} this selector recursively searches the class loader
     * parents until a match is found or a {@code null} parent is found.
     * </p>
     *
     * @param defaultSelector         the selector to consult if no matching log context is found
     * @param checkParentClassLoaders {@code true} if the {@link LogContext log context} could not
     *                                found for the class loader and the {@link ClassLoader#getParent() parent class
     *                                loader} should be checked
     */
    public CallerClassLoaderLogContextSelector(final LogContextSelector defaultSelector, final boolean checkParentClassLoaders) {
        this.defaultSelector = defaultSelector == null ? LogContext.DEFAULT_LOG_CONTEXT_SELECTOR : defaultSelector;
        this.checkParentClassLoaders = checkParentClassLoaders;
    }

    /**
     * Construct a new instance.  If no matching log context is found, the system context is used.
     */
    public CallerClassLoaderLogContextSelector() {
        this(false);
    }

    /**
     * Construct a new instance.  If no matching log context is found, the system context is used.
     * <p>
     * If the {@code checkParentClassLoaders} is set to {@code true} this selector recursively searches the class loader
     * parents until a match is found or a {@code null} parent is found.
     * </p>
     *
     * @param checkParentClassLoaders {@code true} if the {@link LogContext log context} could not
     *                                found for the class loader and the {@link ClassLoader#getParent() parent class
     *                                loader} should be checked
     */
    public CallerClassLoaderLogContextSelector(final boolean checkParentClassLoaders) {
        this(LogContext.DEFAULT_LOG_CONTEXT_SELECTOR, checkParentClassLoaders);
    }

    private final LogContextSelector defaultSelector;

    private final ConcurrentMap<ClassLoader, LogContext> contextMap = new CopyOnWriteMap<ClassLoader, LogContext>();
    private final Set<ClassLoader> logApiClassLoaders = Collections.newSetFromMap(new CopyOnWriteMap<ClassLoader, Boolean>());
    private final boolean checkParentClassLoaders;

    private final PrivilegedAction<LogContext> logContextAction = new PrivilegedAction<LogContext>() {
        public LogContext run() {
            final Class<?> callingClass = JDKSpecific.findCallingClass(logApiClassLoaders);
            return callingClass == null ? defaultSelector.getLogContext() : check(callingClass.getClassLoader());
        }

        private LogContext check(final ClassLoader classLoader) {
            final LogContext context = contextMap.get(classLoader);
            if (context != null) {
                return context;
            }
            final ClassLoader parent = classLoader.getParent();
            if (parent != null && checkParentClassLoaders && ! logApiClassLoaders.contains(parent)) {
                return check(parent);
            }
            return defaultSelector.getLogContext();
        }
    };

    /**
     * {@inheritDoc} This instance will consult the call stack to see if the first callers classloader is associated
     * with a log context. The first caller is determined by the first class loader that is not registered as a
     * {@linkplain #addLogApiClassLoader(ClassLoader) log API}.
     */
    @Override
    public LogContext getLogContext() {
        return AccessController.doPrivileged(logContextAction);
    }

    /**
     * Register a class loader which is a known log API, and thus should be skipped over when searching for the
     * log context to use for the caller class.
     *
     * @param apiClassLoader the API class loader
     *
     * @return {@code true} if this class loader was previously unknown, or {@code false} if it was already registered
     */
    public boolean addLogApiClassLoader(ClassLoader apiClassLoader) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(LOG_API_PERMISSION);
        }
        return logApiClassLoaders.add(apiClassLoader);
    }

    /**
     * Remove a class loader from the known log APIs set.
     *
     * @param apiClassLoader the API class loader
     *
     * @return {@code true} if the class loader was removed, or {@code false} if it was not known to this selector
     */
    public boolean removeLogApiClassLoader(ClassLoader apiClassLoader) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(LOG_API_PERMISSION);
        }
        return logApiClassLoaders.remove(apiClassLoader);
    }

    /**
     * Register a class loader with a log context.  This method requires the {@code registerLogContext} {@link RuntimePermission}.
     *
     * @param classLoader the classloader
     * @param logContext  the log context
     *
     * @throws IllegalArgumentException if the classloader is already associated with a log context
     */
    public void registerLogContext(ClassLoader classLoader, LogContext logContext) throws IllegalArgumentException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(REGISTER_LOG_CONTEXT_PERMISSION);
        }
        if (contextMap.putIfAbsent(classLoader, logContext) != null) {
            throw new IllegalArgumentException("ClassLoader instance is already registered to a log context (" + classLoader + ")");
        }
    }

    /**
     * Unregister a class loader/log context association.  This method requires the {@code unregisterLogContext} {@link RuntimePermission}.
     *
     * @param classLoader the classloader
     * @param logContext  the log context
     *
     * @return {@code true} if the association exists and was removed, {@code false} otherwise
     */
    public boolean unregisterLogContext(ClassLoader classLoader, LogContext logContext) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(UNREGISTER_LOG_CONTEXT_PERMISSION);
        }
        return contextMap.remove(classLoader, logContext);
    }
}
