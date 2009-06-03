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

package org.jboss.logmanager.handlers.log4j;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogManager;
import org.jboss.logmanager.handlers.ExtHandler;

import org.apache.log4j.Appender;
import org.apache.log4j.spi.LoggingEvent;

/**
 * A handler which delegates to a log4j appender.
 */
public final class Log4jAppenderHandler extends ExtHandler {
    private volatile Appender appender = null;

    private static final AtomicReferenceFieldUpdater<Log4jAppenderHandler, Appender> appenderUpdater = AtomicReferenceFieldUpdater.newUpdater(Log4jAppenderHandler.class, Appender.class, "appender");

    /**
     * Construct a new instance.
     *
     * @param appender the appender to delegate to
     */
    public Log4jAppenderHandler(final Appender appender) {
        appenderUpdater.set(this, appender);
    }

    /**
     * Publish a log record.
     *
     * @param record the log record to publish
     */
    public void publish(final ExtLogRecord record) {
        final Appender appender = this.appender;
        if (appender == null) {
            throw new IllegalStateException("Appender is closed");
        }
        final LoggingEvent event = new ConvertedLoggingEvent(record);
        appender.doAppend(event);
    }

    /**
     * Do nothing (there is no equivalent method on log4j appenders).
     */
    public void flush() {
    }

    /**
     * Close the handler and its corresponding appender.
     *
     * @throws SecurityException if you are not allowed to close a handler
     */
    public void close() throws SecurityException {
        LogManager.getLogManager().checkAccess();
        final Appender appender = appenderUpdater.getAndSet(this, null);
        if (appender != null) {
            appender.close();
        }
    }
}
