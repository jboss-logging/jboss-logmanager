/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class CountingOutputStream extends OutputStream {
    private final OutputStream delegate;
    long currentSize;

    CountingOutputStream(final OutputStream delegate) {
        this.delegate = delegate;
        currentSize = 0;
    }

    public void write(final int b) throws IOException {
        delegate.write(b);
        currentSize++;
    }

    public void write(final byte[] b) throws IOException {
        delegate.write(b);
        currentSize += b.length;
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
        delegate.write(b, off, len);
        currentSize += len;
    }

    public void flush() throws IOException {
        delegate.flush();
    }

    public void close() throws IOException {
        delegate.close();
    }
}
