/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
import java.util.concurrent.ConcurrentMap;
import java.util.EnumSet;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.Option.IDENTITY_COMPARISONS;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.ReferenceType.WEAK;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.DEFAULT_CONCURRENCY_LEVEL;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.DEFAULT_LOAD_FACTOR;
import static org.jboss.logmanager.ConcurrentReferenceHashMap.DEFAULT_INITIAL_CAPACITY;

/**
 * A log context selector which chooses a log context based on the caller's classloader.  This selector maintains
 * weak references to the classloader as well as the log context; if either is collected, the association is
 * broken.  Therefore, strong references must be kept external to this class.
 */
public final class ClassLoaderLogContextSelector implements LogContextSelector {

    private static final Permission REGISTER_LOG_CONTEXT_PERMISSION = new RuntimePermission("registerLogContext", null);
    private static final Permission UNREGISTER_LOG_CONTEXT_PERMISSION = new RuntimePermission("unregisterLogContext", null);

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

    private final ConcurrentMap<ClassLoader, LogContext> contextMap =
            new ConcurrentReferenceHashMap<ClassLoader, LogContext>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL, WEAK, WEAK, EnumSet.of(IDENTITY_COMPARISONS));

    /**
     * {@inheritDoc}  This instance will consult the call stack to see if any calling classloader is associated
     * with any log context.
     */
    public LogContext getLogContext() {
        for (Class caller : GATEWAY.getClassContext()) {
            final LogContext context = contextMap.get(caller.getClassLoader());
            if (context != null) {
                return context;
            }
        }
        return defaultSelector.getLogContext();
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
