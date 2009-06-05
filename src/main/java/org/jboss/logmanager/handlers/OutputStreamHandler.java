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
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.Closeable;
import java.io.Flushable;
import java.security.Permission;

import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.LoggingPermission;

/**
 * An output stream handler that supports autoflush and extended log records.  No records will be logged until an
 * output stream is configured.
 */
public class OutputStreamHandler extends ExtHandler {

    private volatile boolean autoflush = false;
    private final Object outputLock = new Object();
    private OutputStream outputStream;
    private Writer writer;

    /**
     * Construct a new instance.
     *
     * @param formatter the formatter to use
     */
    public OutputStreamHandler(final Formatter formatter) {
        setFormatter(formatter);
    }

    /**
     * Construct a new instance.
     *
     * @param outputStream the output stream to use
     * @param formatter the formatter to use
     */
    public OutputStreamHandler(final OutputStream outputStream, final Formatter formatter) {
        setFormatter(formatter);
        setOutputStream(outputStream);
    }

    /**
     * Determine whether autoflush is currently enabled.
     *
     * @return {@code true} if autoflush is enabled
     */
    public boolean isAutoflush() {
        return autoflush;
    }

    /**
     * Change the autoflush status.
     *
     * @param autoflush {@code true} to enable autoflush, {@code false} to disable it
     * @throws SecurityException if you do not have sufficient permission to invoke this operation
     */
    public void setAutoflush(final boolean autoflush) throws SecurityException {
        checkControl();
        this.autoflush = autoflush;
    }

    /**
     * Set the target encoding.
     *
     * @param encoding the new encoding
     * @throws SecurityException if you do not have sufficient permission to invoke this operation
     * @throws UnsupportedEncodingException if the specified encoding is not supported
     */
    public void setEncoding(final String encoding) throws SecurityException, UnsupportedEncodingException {
        checkControl();
        super.setEncoding(encoding);
        synchronized (outputLock) {
            final Writer writer = this.writer;
            if (writer == null) {
                return;
            }
            closeWriter();
            this.writer = encoding == null ? new OutputStreamWriter(outputStream) : new OutputStreamWriter(outputStream, encoding);
        }
    }

    /**
     * Set the output stream to write to.
     *
     * @param newOutputStream the new output stream or {@code null} for none
     */
    public void setOutputStream(final OutputStream newOutputStream) {
        checkControl();
        if (newOutputStream == null) {
            closeStream();
            return;
        }
        final Writer newWriter;
        try {
            final String encoding = getEncoding();
            newWriter = encoding == null ? new OutputStreamWriter(newOutputStream) : new OutputStreamWriter(newOutputStream, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("The specified encoding is invalid");
        } catch (Exception e) {
            reportError("Error opeing output stream", e, ErrorManager.OPEN_FAILURE);
            return;
        }
        synchronized (outputLock) {
            closeStream();
            outputStream = newOutputStream;
            writer = newWriter;
            try {
                writer.write(getFormatter().getHead(this));
            } catch (Exception e) {
                reportError("Error writing section header", e, ErrorManager.WRITE_FAILURE);
            }
        }
    }

    /**
     * Publish a log record.
     *
     * @param record the log record to publish
     */
    public void publish(final ExtLogRecord record) {
        if (isLoggable(record)) {
            final String formatted;
            final Formatter formatter = getFormatter();
            try {
                formatted = formatter.format(record);
            } catch (Exception ex) {
                reportError("Formatting error", ex, ErrorManager.FORMAT_FAILURE);
                return;
            }
            try {
                synchronized (outputLock) {
                    final Writer writer = this.writer;
                    if (writer == null) {
                        return;
                    }
                    writer.write(formatted);
                }
            } catch (Exception ex) {
                reportError("Error writing log message", ex, ErrorManager.WRITE_FAILURE);
                return;
            }
            if (autoflush) flush();
        }
    }

    /**
     * Flush this logger.
     */
    public void flush() {
        synchronized (outputLock) {
            safeFlush(writer);
        }
    }

    private void safeClose(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Exception e) {
            reportError("Error closing resource", e, ErrorManager.CLOSE_FAILURE);
        }
    }

    private void safeFlush(Flushable f) {
        try {
            if (f != null) f.flush();
        } catch (IOException e) {
            reportError("Error on flush", e, ErrorManager.FLUSH_FAILURE);
        }
    }

    private void closeWriter() {
        safeFlush(writer);
        writer = null;
    }

    private void closeStream() {
        synchronized (outputLock) {
            final Writer writer = this.writer;
            if (writer == null) {
                return;
            }
            try {
                writer.write(getFormatter().getTail(this));
            } catch (Exception ex) {
                reportError("Error writing section tail", ex, ErrorManager.WRITE_FAILURE);
            }
            safeFlush(writer);
            safeClose(writer);
            this.writer = null;
            outputStream = null;
        }
    }

    /**
     * Close this logger.
     *
     * @throws SecurityException if you do not have sufficient permission to invoke this operation
     */
    public void close() throws SecurityException {
        checkControl();
        closeStream();
    }

    private static void checkControl() throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONTROL_PERMISSION);
        }
    }

    private static final Permission CONTROL_PERMISSION = new LoggingPermission("control", null);
}
