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

/**
 * A log context selector which stores the chosen log context in a thread-local.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ThreadLocalLogContextSelector implements LogContextSelector {

    private final Object securityKey;
    private final LogContextSelector delegate;
    private final ThreadLocal<LogContext> context = new ThreadLocal<LogContext>();

    /**
     * Construct a new instance.
     *
     * @param delegate the selector to delegate to if no context is chosen
     */
    public ThreadLocalLogContextSelector(final LogContextSelector delegate) {
        this(null, delegate);
    }

    /**
     * Construct a new instance.
     *
     * @param securityKey the security key required to push or pop a log context.
     * @param delegate the selector to delegate to if no context is chosen
     */
    public ThreadLocalLogContextSelector(final Object securityKey, final LogContextSelector delegate) {
        this.securityKey = securityKey;
        this.delegate = delegate;
    }

    @Override
    public LogContext getLogContext() {
        final LogContext localContext = context.get();
        return localContext != null ? localContext : delegate.getLogContext();
    }

    /**
     * Get and set the log context.
     *
     * @param securityKey the security key to check (ignored if none was set on construction)
     * @param newValue the new log context value, or {@code null} to clear
     * @return the previous log context value, or {@code null} if none was set
     */
    public LogContext getAndSet(Object securityKey, LogContext newValue) {
        if (this.securityKey != null && securityKey != this.securityKey) {
            throw new SecurityException("Invalid security key for ThreadLocalLogContextSelector modification");
        }
        try {
            return context.get();
        } finally {
            if (newValue == null) context.remove(); else context.set(newValue);
        }
    }
}
