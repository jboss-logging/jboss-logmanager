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

import org.jboss.logmanager.formatters.Formatters;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import java.util.logging.ErrorManager;
import java.util.logging.Formatter;

/**
 * An output stream handler which supports any {@code OutputStream}, using the specified encoding.  If no encoding is
 * specified, the platform default is used.
 */
public class OutputStreamHandler extends WriterHandler {

    private OutputStream outputStream;

    /**
     * Construct a new instance with no formatter.
     */
    public OutputStreamHandler() {
        setFormatter(Formatters.nullFormatter());
    }

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
     * Get the target encoding.
     *
     * @return the target encoding, or {@code null} if the platform default is being used
     */
    public String getEncoding() {
        synchronized (outputLock) {
            return super.getEncoding();
        }
    }

    /**
     * Set the target encoding.
     *
     * @param encoding the new encoding
     * @throws SecurityException if you do not have sufficient permission to invoke this operation
     * @throws java.io.UnsupportedEncodingException if the specified encoding is not supported
     */
    public void setEncoding(final String encoding) throws SecurityException, UnsupportedEncodingException {
        // superclass checks access
        synchronized (outputLock) {
            super.setEncoding(encoding);
            final OutputStream outputStream = this.outputStream;
            updateWriter(outputStream, encoding);
        }
    }

    /** {@inheritDoc}  Setting a writer will replace any target output stream. */
    public void setWriter(final Writer writer) {
        synchronized (outputLock) {
            super.setWriter(writer);
            outputStream = null;
        }
    }

    /**
     * Set the output stream to write to.
     *
     * @param outputStream the new output stream or {@code null} for none
     */
    public void setOutputStream(final OutputStream outputStream) {
        checkAccess();
        try {
            synchronized (outputLock) {
                this.outputStream = outputStream;
                updateWriter(outputStream, getEncoding());
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("The specified encoding is invalid");
        } catch (Exception e) {
            reportError("Error opening output stream", e, ErrorManager.OPEN_FAILURE);
            return;
        }
    }

    private void updateWriter(final OutputStream newOutputStream, final String encoding) throws UnsupportedEncodingException {
        final UninterruptibleOutputStream outputStream = new UninterruptibleOutputStream(newOutputStream);
        super.setWriter(newOutputStream == null ? null : encoding == null ? new OutputStreamWriter(outputStream) : new OutputStreamWriter(outputStream, encoding));
    }
}
