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

import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.LoggingPermission;

import java.io.Flushable;
import java.security.Permission;

/**
 * An extended logger handler.  Use this class as a base class for log handlers which require {@code ExtLogRecord}
 * instances.
 */
public abstract class ExtHandler extends Handler implements Flushable {

    private static final String LOGGER_CLASS_NAME = org.jboss.logmanager.Logger.class.getName();
    private static final Permission CONTROL_PERMISSION = new LoggingPermission("control", null);

    /** {@inheritDoc} */
    public final void publish(final LogRecord record) {
        publish((record instanceof ExtLogRecord) ? (ExtLogRecord) record : new ExtLogRecord(record, LOGGER_CLASS_NAME));
    }

    /**
     * Publish an {@code ExtLogRecord}.
     * <p/>
     * The logging request was made initially to a Logger object, which initialized the LogRecord and forwarded it here.
     * <p/>
     * The {@code ExtHandler} is responsible for formatting the message, when and if necessary. The formatting should
     * include localization.
     *
     * @param record the log record to publish
     */
    public abstract void publish(final ExtLogRecord record);

    /**
     * Check access.
     *
     * @throws SecurityException if a security manager is installed and the caller does not have the {@code "control" LoggingPermission}
     */
    protected static void checkAccess() throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONTROL_PERMISSION);
        }
    }
}
