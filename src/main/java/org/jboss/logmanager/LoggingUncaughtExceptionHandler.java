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

/**
 * Logging uncaught exception handler.
 */
public final class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger log;

    /**
     * Create a new instance.
     *
     * @param log the logger to log the uncaught exception to
     */
    public LoggingUncaughtExceptionHandler(final Logger log) {
        this.log = log;
    }

    /**
     * Method invoked when the given thread terminates due to the given uncaught exception. <p>Any exception thrown by this
     * method will be ignored by the Java Virtual Machine.
     *
     * @param t the thread
     * @param e the exception
     */
    public void uncaughtException(final Thread t, final Throwable e) {
        log.log(Level.ERROR, "Uncaught exception", e);
    }
}
