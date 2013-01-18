/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat Inc., and individual contributors
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
