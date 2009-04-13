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

package org.jboss.stdio;

import java.io.InputStream;
import java.io.IOException;

public final class ContextInputStream extends InputStream {
    private static final class StreamHolder extends InheritableThreadLocal<InputStream> {
        private final InputStream initialValue;

        private StreamHolder(final InputStream initialValue) {
            this.initialValue = initialValue;
        }

        protected InputStream initialValue() {
            return initialValue;
        }
    }

    private final ThreadLocal<InputStream> delegateHolder;

    public ContextInputStream(final InputStream defaultDelegate) {
        delegateHolder = new StreamHolder(defaultDelegate);
    }

    public InputStream swapDelegate(final InputStream newDelegate) {
        try {
            return delegateHolder.get();
        } finally {
            delegateHolder.set(newDelegate);
        }
    }

    public static InputStream getAndSetSystemIn(final InputStream newSystemIn) {
        return ((ContextInputStream)System.in).swapDelegate(newSystemIn);
    }

    private InputStream getDelegate() {
        return delegateHolder.get();
    }

    public int read() throws IOException {
        return getDelegate().read();
    }

    public int read(final byte[] b) throws IOException {
        return getDelegate().read(b);
    }

    public int read(final byte[] b, final int off, final int len) throws IOException {
        return getDelegate().read(b, off, len);
    }

    public long skip(final long n) throws IOException {
        return getDelegate().skip(n);
    }

    public int available() throws IOException {
        return getDelegate().available();
    }

    public void close() throws IOException {
        getDelegate().close();
    }

    public void mark(final int readlimit) {
        getDelegate().mark(readlimit);
    }

    public void reset() throws IOException {
        getDelegate().reset();
    }

    public boolean markSupported() {
        return getDelegate().markSupported();
    }
}
