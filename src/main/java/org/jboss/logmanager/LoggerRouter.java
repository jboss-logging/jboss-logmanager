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
 * A router used to route log messages to a specific log context. This is useful when static loggers may be created on
 * one log context and later need to be routed to a different log context.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public abstract class LoggerRouter {

    private final LogContext defaultLogContext;

    /**
     * Creates a new log router.
     *
     * @param defaultLogContext the default log context
     */
    protected LoggerRouter(final LogContext defaultLogContext) {
        this.defaultLogContext = defaultLogContext;
    }

    /**
     * Returns the log context that should be used.
     *
     * @return the log context to use or {@code null} the default loggers
     */
    public abstract LogContext getLogContext();

    /**
     * Attempts to resolve the logger node on the {@linkplain #getLogContext() log context}.
     *
     * @param name the name of the logger node to create
     * @param dft  the default logger node
     *
     * @return the logger node
     */
    LoggerNode resolveLoggerNode(final String name, final LoggerNode dft) {
        final LogContext logContext = getLogContext();
        if (logContext == null || (defaultLogContext.equals(logContext) && dft.getContext().equals(logContext))) {
            return dft;
        }
        return logContext.getRootLoggerNode().getOrCreate(name);
    }
}
