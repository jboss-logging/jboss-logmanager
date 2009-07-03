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

package org.jboss.logmanager.errormanager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.io.PrintStream;

import java.util.logging.ErrorManager;

/**
 * An error manager which runs only once and writes a complete formatted error to {@code System.err}.  Caches
 * an early {@code System.err} in case it is replaced.
 */
public final class OnlyOnceErrorManager extends ErrorManager {
    private static final PrintStream ps = System.err;

    private final AtomicBoolean called = new AtomicBoolean();

    /** {@inheritDoc} */
    public void error(final String msg, final Exception ex, final int code) {
        if (called.getAndSet(true)) {
            return;
        }
        final String codeStr;
        switch (code) {
            case CLOSE_FAILURE: codeStr = "CLOSE_FAILURE"; break;
            case FLUSH_FAILURE: codeStr = "FLUSH_FAILURE"; break;
            case FORMAT_FAILURE: codeStr = "FORMAT_FAILURE"; break;
            case GENERIC_FAILURE: codeStr = "GENERIC_FAILURE"; break;
            case OPEN_FAILURE: codeStr = "OPEN_FAILURE"; break;
            case WRITE_FAILURE: codeStr = "WRITE_FAILURE"; break;
            default: codeStr = "INVALID (" + code + ")"; break;
        }
        ps.printf("LogManager error of type %s: %s\n", codeStr, msg);
        if (ex != null) {
            ex.printStackTrace(ps);
        }
    }
}
