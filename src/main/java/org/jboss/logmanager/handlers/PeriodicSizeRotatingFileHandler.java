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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.logging.ErrorManager;

import org.jboss.logmanager.ExtLogRecord;

/**
 * A file handler which rotates the log at a preset time interval or the size of the log.
 * <p/>
 * The time interval is determined by the content of the suffix string which is passed in to {@link
 * #setSuffix(String)}.
 * <p/>
 * The size interval is determined by the value passed in the {@link #setRotateSize(long)}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PeriodicSizeRotatingFileHandler extends PeriodicRotatingFileHandler {
    // by default, rotate at 10MB
    private long rotateSize = 0xa0000L;
    private int maxBackupIndex = 1;
    private CountingOutputStream outputStream;

    /**
     * Default constructor.
     */
    public PeriodicSizeRotatingFileHandler() {
        super();
    }

    /**
     * Construct a new instance with the given output file.
     *
     * @param fileName the file name
     *
     * @throws java.io.FileNotFoundException if the file could not be found on open
     */
    public PeriodicSizeRotatingFileHandler(final String fileName) throws FileNotFoundException {
        super(fileName);
    }

    /**
     * Construct a new instance with the given output file and append setting.
     *
     * @param fileName the file name
     * @param append   {@code true} to append, {@code false} to overwrite
     *
     * @throws java.io.FileNotFoundException if the file could not be found on open
     */
    public PeriodicSizeRotatingFileHandler(final String fileName, final boolean append) throws FileNotFoundException {
        super(fileName, append);
    }

    /**
     * Construct a new instance with the given output file.
     *
     * @param file   the file
     * @param suffix the format suffix to use
     *
     * @throws java.io.FileNotFoundException if the file could not be found on open
     */
    public PeriodicSizeRotatingFileHandler(final File file, final String suffix) throws FileNotFoundException {
        super(file, suffix);
    }

    /**
     * Construct a new instance with the given output file and append setting.
     *
     * @param file   the file
     * @param suffix the format suffix to use
     * @param append {@code true} to append, {@code false} to overwrite
     *
     * @throws java.io.FileNotFoundException if the file could not be found on open
     */
    public PeriodicSizeRotatingFileHandler(final File file, final String suffix, final boolean append) throws FileNotFoundException {
        super(file, suffix, append);
    }

    /**
     * Construct a new instance with the given output file.
     *
     * @param file           the file
     * @param suffix         the format suffix to use
     * @param rotateSize     the size the file should rotate at
     * @param maxBackupIndex the maximum number of files to backup
     *
     * @throws java.io.FileNotFoundException if the file could not be found on open
     */
    public PeriodicSizeRotatingFileHandler(final File file, final String suffix, final long rotateSize, final int maxBackupIndex) throws FileNotFoundException {
        super(file, suffix);
        this.rotateSize = rotateSize;
        this.maxBackupIndex = maxBackupIndex;
    }

    /**
     * Construct a new instance with the given output file.
     *
     * @param file           the file
     * @param suffix         the format suffix to use
     * @param rotateSize     the size the file should rotate at
     * @param maxBackupIndex the maximum number of files to backup
     * @param append         {@code true} to append, {@code false} to overwrite
     *
     * @throws java.io.FileNotFoundException if the file could not be found on open
     */
    public PeriodicSizeRotatingFileHandler(final File file, final String suffix, final long rotateSize, final int maxBackupIndex, final boolean append) throws FileNotFoundException {
        super(file, suffix, append);
        this.rotateSize = rotateSize;
        this.maxBackupIndex = maxBackupIndex;
    }


    @Override
    public void setOutputStream(final OutputStream outputStream) {
        synchronized (outputLock) {
            this.outputStream = outputStream == null ? null : new CountingOutputStream(outputStream);
            super.setOutputStream(this.outputStream);
        }
    }

    @Override
    public void setFile(final File file) throws FileNotFoundException {
        synchronized (outputLock) {
            super.setFile(file);
            if (outputStream != null)
                outputStream.currentSize = file == null ? 0L : file.length();
        }
    }

    /**
     * Set the rotation size, in bytes.
     *
     * @param rotateSize the number of bytes before the log is rotated
     */
    public void setRotateSize(final long rotateSize) {
        checkAccess(this);
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
        checkAccess(this);
        synchronized (outputLock) {
            this.maxBackupIndex = maxBackupIndex;
        }
    }

    @Override
    protected void preWrite(final ExtLogRecord record) {
        super.preWrite(record);
        final int maxBackupIndex = this.maxBackupIndex;
        final long currentSize = (outputStream == null ? Long.MIN_VALUE : outputStream.currentSize);
        if (currentSize > rotateSize && maxBackupIndex > 0) {
            try {
                final File file = getFile();
                if (file == null) {
                    // no file is set; a direct output stream or writer was specified
                    return;
                }
                // close the old file.
                setFile(null);
                final File fileWithSuffix = new File(file.getAbsolutePath() + getNextSuffix());
                // rotate.  First, drop the max file (if any), then move each file to the next higher slot.
                new File(fileWithSuffix.getAbsolutePath() + "." + maxBackupIndex).delete();
                for (int i = maxBackupIndex - 1; i >= 1; i--) {
                    new File(fileWithSuffix.getAbsolutePath() + "." + i).renameTo(new File(fileWithSuffix.getAbsolutePath() + "." + (i + 1)));
                }
                file.renameTo(new File(fileWithSuffix.getAbsolutePath() + ".1"));
                // start with new file.
                setFile(file);
            } catch (FileNotFoundException e) {
                reportError("Unable to rotate log file", e, ErrorManager.OPEN_FAILURE);
            }
        }
    }
}
