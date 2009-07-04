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

import java.io.OutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.ErrorManager;

public class SizeRotatingFileHandler extends FileHandler {
    // by default, rotate at 10MB
    private long rotateSize = 0xa0000L;
    private long currentSize;
    private int maxBackupIndex = 1;

    /** {@inheritDoc} */
    public void setOutputStream(final OutputStream outputStream) {
        super.setOutputStream(outputStream == null ? null : new CountingOutputStream(outputStream));
    }

    /** {@inheritDoc} */
    public void setFile(final File file) throws FileNotFoundException {
        synchronized (outputLock) {
            super.setFile(file);
            currentSize = file == null ? 0L : file.length();
        }
    }

    /**
     * Set the rotation size, in bytes.
     *
     * @param rotateSize the number of bytes before the log is rotated
     */
    public void setRotateSize(final long rotateSize) {
        checkAccess();
        synchronized (outputLock) {
            this.rotateSize = rotateSize;
        }
    }

    /**
     * Set the maximum backup index (the number of log files to keep around).
     *
     * @param maxBackupIndex the maximum backup index
     */
    public void setMaxBackupIndex(final int maxBackupIndex) {
        checkAccess();
        synchronized (outputLock) {
            this.maxBackupIndex = maxBackupIndex;
        }
    }

    /** {@inheritDoc} */
    protected void preWrite(final ExtLogRecord record) {
        final int maxBackupIndex = this.maxBackupIndex;
        if (currentSize > rotateSize && maxBackupIndex > 0) {
            try {
                final File file = getFile();
                if (file == null) {
                    // no file is set; a direct output stream or writer was specified
                    return;
                }
                // close the old file.
                setFile(null);
                // rotate.  First, drop the max file (if any), then move each file to the next higher slot.
                new File(file.getAbsolutePath() + "." + maxBackupIndex).delete();
                for (int i = maxBackupIndex - 1; i > 1; i--) {
                    new File(file.getAbsolutePath() + "." + i).renameTo(new File(file.getAbsolutePath() + "." + (i - 1)));
                }
                file.renameTo(new File(file.getAbsolutePath() + ".1"));
                // start with new file.
                setFile(file);
            } catch (FileNotFoundException e) {
                reportError("Unable to rotate log file", e, ErrorManager.OPEN_FAILURE);
            }
        }
    }

    private final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;

        private CountingOutputStream(final OutputStream delegate) {
            this.delegate = delegate;
        }

        public void write(final int b) throws IOException {
            delegate.write(b);
            currentSize ++;
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
}
