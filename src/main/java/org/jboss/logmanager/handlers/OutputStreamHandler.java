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
            if (this.outputStream != null) {
                final OutputStream outputStream = this.outputStream;
                updateWriter(outputStream, encoding);
           }
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
        checkAccess(this);
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
