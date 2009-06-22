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

package org.jboss.logmanager.formatters;

import java.io.Writer;
import java.io.IOException;

final class StringBuilderWriter extends Writer {

    private final StringBuilder builder;

    public StringBuilderWriter(final StringBuilder builder) {
        this.builder = builder;
    }

    public void write(final char[] cbuf, final int off, final int len) {
        builder.append(cbuf, off, len);
    }

    public void write(final int c) {
        builder.append(c);
    }

    public void write(final char[] cbuf) {
        builder.append(cbuf);
    }

    public void write(final String str) throws IOException {
        builder.append(str);
    }

    public void write(final String str, final int off, final int len) throws IOException {
        builder.append(str, off, len);
    }

    public void flush() throws IOException {
    }

    public void close() throws IOException {
    }
}
