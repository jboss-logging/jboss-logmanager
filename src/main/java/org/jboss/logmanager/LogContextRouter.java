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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

/**
 * A router used to route log messages to a specific log context based on the current threads
 * {@linkplain Thread#getContextClassLoader() context class loader}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public class LogContextRouter {
    private static final LogContextRouter INSTANCE = new LogContextRouter();
    private static volatile boolean ENABLED = false;
    private final Map<ClassLoader, ContextualLoggerNodes> contexts;

    private LogContextRouter() {
        contexts = new HashMap<>();
    }

    /**
     * Gets the instance of the router.
     *
     * @return the router
     */
    public static LogContextRouter getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if the routing is enabled.
     *
     * @return {@code true} if the router is enabled, otherwise {@code false}
     */
    static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Gets or creates a logger node.
     *
     * @param name the name of the logger nod to create
     *
     * @return the logger node or {@code null} if there is no {@link LogContext} associated with the
     * {@linkplain Thread#getContextClassLoader() TCCL}
     */
    LoggerNode getLoggerNode(final String name) {
        final ClassLoader tccl = getTccl();
        if (tccl == null) {
            return null;
        }
        synchronized (contexts) {
            final ContextualLoggerNodes value = contexts.get(tccl);
            return value == null ? null : value.getLoggerNode(name);
        }
    }

    /**
     * Registers the log context the class loader should route log messages to.
     *
     * @param cl         the class loader which should route messages based on the {@linkplain Thread#getContextClassLoader() TCCL}
     * @param logContext the context messages should be routed to
     *
     * @return this router
     */
    public LogContextRouter register(final ClassLoader cl, final LogContext logContext) {
        synchronized (contexts) {
            contexts.putIfAbsent(cl, new ContextualLoggerNodes(logContext));
            ENABLED = !contexts.isEmpty();
        }
        return this;
    }

    /**
     * Removes class loader from routing.
     *
     * @param cl the class loader to remove from routing
     *
     * @return this router
     */
    public LogContextRouter unregister(final ClassLoader cl) {
        synchronized (contexts) {
            final ContextualLoggerNodes removed = contexts.remove(cl);
            if (removed != null) {
                removed.clear();
            }
            ENABLED = !contexts.isEmpty();
        }
        return this;
    }

    @SuppressWarnings("Convert2Lambda")
    private static ClassLoader getTccl() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        }
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        });
    }

    private static class ContextualLoggerNodes {
        final LogContext logContext;
        final Map<String, LoggerNode> nodes;

        private ContextualLoggerNodes(final LogContext logContext) {
            this.logContext = logContext;
            nodes = new CopyOnWriteWeakMap<>();
        }

        LoggerNode getLoggerNode(final String name) {
            LoggerNode result = nodes.get(name);
            if (result == null) {
                result = logContext.getRootLoggerNode().getOrCreate(name);
                final LoggerNode appearing = nodes.putIfAbsent(name, result);
                if (appearing != null) {
                    return appearing;
                }
            }
            return result;
        }

        void clear() {
            nodes.clear();
        }
    }
}
