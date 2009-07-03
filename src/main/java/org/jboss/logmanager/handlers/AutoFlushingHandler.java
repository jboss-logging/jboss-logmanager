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

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Formatter;
import java.util.logging.Filter;
import java.util.logging.ErrorManager;
import java.util.logging.Level;

/**
 * A handler which wraps another handler, forcing a flush after each successful message publication.
 */
public class AutoFlushingHandler extends Handler {
    private final Handler delegate;

    /**
     * Construct a new instance.
     *
     * @param delegate the handler to delegate to
     */
    public AutoFlushingHandler(final Handler delegate) {
        this.delegate = delegate;
    }

    /** {@inheritDoc} */
    public void publish(final LogRecord record) {
        final Handler delegate = this.delegate;
        delegate.publish(record);
        delegate.flush();
    }

    /**
     * Not supported.
     *
     * @param newFormatter ignored
     * @throws UnsupportedOperationException always
     */
    public void setFormatter(final Formatter newFormatter) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     *
     * @param encoding ignored
     * @throws UnsupportedOperationException always
     */
    public void setEncoding(final String encoding) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     *
     * @param newFilter ignored
     * @throws UnsupportedOperationException always
     */
    public void setFilter(final Filter newFilter) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     *
     * @param em ignored
     * @throws UnsupportedOperationException always
     */
    public void setErrorManager(final ErrorManager em) throws UnsupportedOperationException{
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     *
     * @param newLevel ignored
     * @throws UnsupportedOperationException always
     */
    public void setLevel(final Level newLevel) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    public void flush() {
        delegate.flush();
    }

    /** {@inheritDoc}  This implementation does nothing. */
    public void close() throws SecurityException {
    }
}
