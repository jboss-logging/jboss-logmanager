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

package org.jboss.logmanager;

import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.concurrent.ConcurrentMap;

import static java.lang.System.getSecurityManager;
import static java.lang.Thread.currentThread;
import static java.security.AccessController.doPrivileged;

/**
 * A log context selector which chooses a log context based on the thread context classloader.
 */
public final class ContextClassLoaderLogContextSelector implements LogContextSelector {

    private static final Permission REGISTER_LOG_CONTEXT_PERMISSION = new RuntimePermission("registerLogContext", null);
    private static final Permission UNREGISTER_LOG_CONTEXT_PERMISSION = new RuntimePermission("unregisterLogContext", null);

    /**
     * Construct a new instance.  If no matching log context is found, the provided default selector is consulted.
     *
     * @param defaultSelector the selector to consult if no matching log context is found
     */
    public ContextClassLoaderLogContextSelector(final LogContextSelector defaultSelector) {
        this.defaultSelector = defaultSelector;
    }

    /**
     * Construct a new instance.  If no matching log context is found, the system context is used.
     */
    public ContextClassLoaderLogContextSelector() {
        this(LogContext.DEFAULT_LOG_CONTEXT_SELECTOR);
    }

    private final LogContextSelector defaultSelector;

    private final ConcurrentMap<ClassLoader, LogContext> contextMap = new CopyOnWriteMap<ClassLoader, LogContext>();

    private final PrivilegedAction<LogContext> logContextAction = new PrivilegedAction<LogContext>() {
        public LogContext run() {
            ClassLoader cl = currentThread().getContextClassLoader();
            if (cl != null) {
                final LogContext mappedContext = contextMap.get(cl);
                if (mappedContext != null) {
                    return mappedContext;
                }
            }
            return defaultSelector.getLogContext();
        }
    };

    public LogContext getLogContext() {
        return System.getSecurityManager() == null? logContextAction.run() :
                doPrivileged(logContextAction);
    }

    /**
     * Register a class loader with a log context.  This method requires the {@code registerLogContext} {@link RuntimePermission}.
     *
     * @param classLoader the classloader
     * @param logContext the log context
     * @throws IllegalArgumentException if the classloader is already associated with a log context
     */
    public void registerLogContext(ClassLoader classLoader, LogContext logContext) throws IllegalArgumentException {
        final SecurityManager sm = getSecurityManager();
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
     * @param logContext the log context
     * @return {@code true} if the association exists and was removed, {@code false} otherwise
     */
    public boolean unregisterLogContext(ClassLoader classLoader, LogContext logContext) {
        final SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPermission(UNREGISTER_LOG_CONTEXT_PERMISSION);
        }
        return contextMap.remove(classLoader, logContext);
    }
}
