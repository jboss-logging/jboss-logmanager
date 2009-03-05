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

import java.io.Writer;
import java.io.IOException;

import java.util.logging.Logger;

/**
 * A writer which sends its data to a logger.
 */
public final class LoggingWriter extends Writer {

    @SuppressWarnings({ "NonConstantLogger" })
    private final Logger log;
    private final java.util.logging.Level level;
    private final StringBuilder buffer = new StringBuilder();

    /**
     * Construct a new instance.
     *
     * @param category the log category to use
     * @param level the level at which to log messages
     */
    public LoggingWriter(final String category, final java.util.logging.Level level) {
        this.level = level;
        log = Logger.getLogger(category);
    }

    /** {@inheritDoc} */
    @Override
    public void write(final int c) throws IOException {
        synchronized (buffer) {
            if (c == '\n') {
                log.log(level, buffer.toString());
                buffer.setLength(0);
            } else {
                buffer.append((char) c);
            }
        }
    }

    /** {@inheritDoc} */
    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        synchronized (buffer) {
            int mark = 0;
            int i;
            for (i = 0; i < len; i++) {
                final char c = cbuf[off + i];
                if (c == '\n') {
                    buffer.append(cbuf, mark + off, i - mark);
                    log.log(level, buffer.toString());
                    buffer.setLength(0);
                    mark = i + 1;
                }
            }
            buffer.append(cbuf, mark + off, i - mark);
        }
    }

    /** {@inheritDoc} */
    public void flush() throws IOException {
        synchronized (buffer) {
            if (buffer.length() > 0) {
                buffer.append(" >>> FLUSH");
                log.log(level, buffer.toString());
                buffer.setLength(0);
            }
        }
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        // ignore
    }
}
