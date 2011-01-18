/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
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

import java.io.Writer;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

import java.util.logging.ErrorManager;
import java.util.logging.Formatter;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtHandler;

/**
 * A handler which writes to any {@code Writer}.
 */
public class WriterHandler extends ExtHandler {

    protected final Object outputLock = new Object();
    private Writer writer;

    /** {@inheritDoc} */
    protected void doPublish(final ExtLogRecord record) {
        final String formatted;
        final Formatter formatter = getFormatter();
        try {
            formatted = formatter.format(record);
        } catch (Exception ex) {
            reportError("Formatting error", ex, ErrorManager.FORMAT_FAILURE);
            return;
        }
        if (formatted.length() == 0) {
            // nothing to write; don't bother
            return;
        }
        try {
            synchronized (outputLock) {
                if (writer == null) {
                    return;
                }
                preWrite(record);
                final Writer writer = this.writer;
                if (writer == null) {
                    return;
                }
                writer.write(formatted);
                // only flush if something was written
                super.doPublish(record);
            }
        } catch (Exception ex) {
            reportError("Error writing log message", ex, ErrorManager.WRITE_FAILURE);
            return;
        }
    }

    /**
     * Execute any pre-write policy, such as file rotation.  The write lock is held during this method, so make
     * it quick.  The default implementation does nothing.
     *
     * @param record the record about to be logged
     */
    protected void preWrite(final ExtLogRecord record) {
        // do nothing by default
    }

    /**
     * Set the writer.  The writer will then belong to this handler; when the handler is closed or a new writer is set,
     * this writer will be closed.
     *
     * @param writer the new writer, or {@code null} to disable logging
     */
    public void setWriter(final Writer writer) {
        checkAccess();
        final Writer oldWriter;
        synchronized (outputLock) {
            oldWriter = this.writer;
            writeTail(oldWriter);
            safeFlush(oldWriter);
            writeHead(writer);
            this.writer = writer;
        }
        safeClose(oldWriter);
    }

    private void writeHead(final Writer writer) {
        if (writer != null) {
            try {
                writer.write(getFormatter().getHead(this));
            } catch (Exception e) {
                reportError("Error writing section header", e, ErrorManager.WRITE_FAILURE);
            }
        }
    }

    private void writeTail(final Writer writer) {
        if (writer != null) {
            try {
                writer.write(getFormatter().getTail(this));
            } catch (Exception ex) {
                reportError("Error writing section tail", ex, ErrorManager.WRITE_FAILURE);
            }
        }
    }

    /**
     * Flush this logger.
     */
    public void flush() {
        // todo - maybe this synch is not really needed... if there's a perf detriment, drop it
        synchronized (outputLock) {
            safeFlush(writer);
        }
    }

    /**
     * Close this logger.
     *
     * @throws SecurityException if the caller does not have sufficient permission
     */
    public void close() throws SecurityException {
        checkAccess();
        setWriter(null);
    }

    /**
     * Safely close the resource, reporting an error if the close fails.
     *
     * @param c the resource
     */
    protected void safeClose(Closeable c) {
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
}
