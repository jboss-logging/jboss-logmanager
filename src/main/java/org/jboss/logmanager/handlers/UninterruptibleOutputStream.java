/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat Inc., and individual contributors
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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;

import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;

/**
 * An output stream which is not interruptible.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class UninterruptibleOutputStream extends FilterOutputStream {

    /**
     * Construct a new instance.
     *
     * @param out the delegate stream
     */
    public UninterruptibleOutputStream(final OutputStream out) {
        super(out);
    }

    /**
     * Write the given byte uninterruptibly.
     *
     * @param b the byte to write
     * @throws IOException if an error occurs
     */
    public void write(final int b) throws IOException {
        boolean intr = false;
        try {
            for (;;) try {
                super.write(b);
                return;
            } catch (InterruptedIOException e) {
                final int transferred = e.bytesTransferred;
                if (transferred == 1) {
                    return;
                }
                intr |= interrupted();
            }
        } finally {
            if (intr) {
                currentThread().interrupt();
            }
        }
    }

    /**
     * Write the given bytes uninterruptibly.
     *
     * @param b the bytes to write
     * @param off the offset into the array
     * @param len the length of the array to write
     * @throws IOException if an error occurs
     */
    public void write(final byte[] b, int off, int len) throws IOException {
        boolean intr = false;
        try {
            while (len > 0) try {
                super.write(b, off, len);
                return;
            } catch (InterruptedIOException e) {
                final int transferred = e.bytesTransferred;
                if (transferred > 0) {
                    off += transferred;
                    len -= transferred;
                }
                intr |= interrupted();
            }
        } finally {
            if (intr) {
                currentThread().interrupt();
            }
        }
    }

    /**
     * Flush the stream uninterruptibly.
     *
     * @throws IOException if an error occurs
     */
    public void flush() throws IOException {
        boolean intr = false;
        try {
            for (;;) try {
                super.flush();
                return;
            } catch (InterruptedIOException e) {
                intr |= interrupted();
            }
        } finally {
            if (intr) {
                currentThread().interrupt();
            }
        }
    }

    /**
     * Close the stream uninterruptibly.
     *
     * @throws IOException if an error occurs
     */
    public void close() throws IOException {
        boolean intr = false;
        try {
            for (;;) try {
                super.close();
                return;
            } catch (InterruptedIOException e) {
                intr |= interrupted();
            }
        } finally {
            if (intr) {
                currentThread().interrupt();
            }
        }
    }

    /**
     * Get the string representation of this stream.
     *
     * @return the string
     */
    public String toString() {
        return "uninterruptible " + out.toString();
    }
}
