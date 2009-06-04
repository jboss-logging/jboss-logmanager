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

package org.jboss.logmanager.handlers;

import java.io.Flushable;

import java.util.logging.Handler;

/**
 * Handler utility methods.
 */
public final class Handlers {

    private Handlers() {
    }

    /**
     * Create a wrapper that exposes the handler's close and flush methods via the I/O API.
     *
     * @param handler the logging handler
     * @return the wrapper
     */
    public static Flushable wrap(final Handler handler) {
        return handler instanceof Flushable ? (Flushable) handler : new Flushable() {
            public void close() {
                handler.close();
            }

            public void flush() {
                handler.flush();
            }
        };
    }

    /**
     * Create a {@code Runnable} task that flushes a handler.
     *
     * @param handler the handler
     * @return a flushing task
     */
    public static Runnable flusher(final Handler handler) {
        return new Runnable() {
            public void run() {
                handler.flush();
            }
        };
    }
}
