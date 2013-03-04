/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.logmanager;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Permission;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A log context selector which chooses a log context based on the caller's classloader.
 */
public final class ClassLoaderLogContextSelector implements LogContextSelector {

    private static final Permission REGISTER_LOG_CONTEXT_PERMISSION = new RuntimePermission("registerLogContext", null);
    private static final Permission UNREGISTER_LOG_CONTEXT_PERMISSION = new RuntimePermission("unregisterLogContext", null);
    private static final Permission LOG_API_PERMISSION = new RuntimePermission("logApiPermission", null);

    /**
     * Construct a new instance.  If no matching log context is found, the provided default selector is consulted.
     *
     * @param defaultSelector the selector to consult if no matching log context is found
     */
    public ClassLoaderLogContextSelector(final LogContextSelector defaultSelector) {
        this.defaultSelector = defaultSelector;
    }

    /**
     * Construct a new instance.  If no matching log context is found, the system context is used.
     */
    public ClassLoaderLogContextSelector() {
        this(LogContext.DEFAULT_LOG_CONTEXT_SELECTOR);
    }

    private static final class Gateway extends SecurityManager {
        protected Class[] getClassContext() {
            return super.getClassContext();
        }
    }

    private static final Gateway GATEWAY;

    static {
        GATEWAY = AccessController.doPrivileged(new PrivilegedAction<Gateway>() {
            public Gateway run() {
                return new Gateway();
            }
        });
    }

    private final LogContextSelector defaultSelector;

    private final ConcurrentMap<ClassLoader, LogContext> contextMap = new CopyOnWriteMap<ClassLoader, LogContext>();
    private final Set<ClassLoader> logApiClassLoaders = Collections.newSetFromMap(new CopyOnWriteMap<ClassLoader, Boolean>());

    private final PrivilegedAction<LogContext> logContextAction = new PrivilegedAction<LogContext>() {
        public LogContext run() {
            for (Class caller : GATEWAY.getClassContext()) {
                final ClassLoader classLoader = caller.getClassLoader();
                if (classLoader != null && ! logApiClassLoaders.contains(classLoader)) {
                    final LogContext context = contextMap.get(classLoader);
                    if (context != null) {
                        return context;
                    }
                }
            }
            return defaultSelector.getLogContext();
        }
    };

    /**
     * {@inheritDoc}  This instance will consult the call stack to see if any calling classloader is associated
     * with any log context.
     */
    public LogContext getLogContext() {
        return AccessController.doPrivileged(logContextAction);
    }

    /**
     * Register a class loader which is a known log API, and thus should be skipped over when searching for the
     * log context to use for the caller class.
     *
     * @param apiClassLoader the API class loader
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
     * @param logContext the log context
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
     * @param logContext the log context
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
