/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
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
    private final AccessControlContext acc = AccessController.getContext();
    // by default, rotate at 10MB
    private long rotateSize = 0xa00000L;
    private int maxBackupIndex = 1;
    private CountingOutputStream outputStream;
    private boolean rotateOnBoot;

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
     * @throws FileNotFoundException if the file could not be found on open
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
     * @throws FileNotFoundException if the file could not be found on open
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
     * @throws FileNotFoundException if the file could not be found on open
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
     * @throws FileNotFoundException if the file could not be found on open
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
     * @throws FileNotFoundException if the file could not be found on open
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
     * @throws FileNotFoundException if the file could not be found on open
     */
    public PeriodicSizeRotatingFileHandler(final File file, final String suffix, final long rotateSize, final int maxBackupIndex, final boolean append) throws FileNotFoundException {
        super(file, suffix, append);
        this.rotateSize = rotateSize;
        this.maxBackupIndex = maxBackupIndex;
    }


    @Override
    public void setOutputStream(final OutputStream outputStream) {
        lock.lock();
        try {
            this.outputStream = outputStream == null ? null : new CountingOutputStream(outputStream);
            super.setOutputStream(this.outputStream);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws RuntimeException if there is an attempt to rotate file and the rotation fails
     */
    @Override
    public void setFile(final File file) throws FileNotFoundException {
        checkAccess();
        lock.lock();
        try {
            // Check for a rotate
            if (rotateOnBoot && maxBackupIndex > 0 && file != null && file.exists() && file.length() > 0L) {
                final String suffix = getNextSuffix();
                final SuffixRotator suffixRotator = getSuffixRotator();
                if (suffixRotator != SuffixRotator.EMPTY && suffix != null) {
                    // Make sure any previous files are closed before we attempt to rotate
                    setFileInternal(null, false);
                    suffixRotator.rotate(getErrorManager(), file.toPath(), suffix, maxBackupIndex);
                }
            }
            setFileInternal(file, false);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Indicates whether or a not the handler should rotate the file before the first log record is written.
     *
     * @return {@code true} if file should rotate on boot, otherwise {@code false}/
     */
    public boolean isRotateOnBoot() {
        lock.lock();
        try {
            return rotateOnBoot;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set to a value of {@code true} if the file should be rotated before the a new file is set. The rotation only
     * happens if the file names are the same and the file has a {@link File#length() length} greater than 0.
     *
     * @param rotateOnBoot {@code true} to rotate on boot, otherwise {@code false}
     */
    public void setRotateOnBoot(final boolean rotateOnBoot) {
        checkAccess();
        lock.lock();
        try {
            this.rotateOnBoot = rotateOnBoot;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the rotation size, in bytes.
     *
     * @param rotateSize the number of bytes before the log is rotated
     */
    public void setRotateSize(final long rotateSize) {
        checkAccess();
        lock.lock();
        try {
            this.rotateSize = rotateSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the maximum backup index (the number of log files to keep around).
     *
     * @param maxBackupIndex the maximum backup index
     */
    public void setMaxBackupIndex(final int maxBackupIndex) {
        checkAccess();
        lock.lock();
        try {
            this.maxBackupIndex = maxBackupIndex;
        } finally {
            lock.unlock();
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
                setFileInternal(null, true);
                getSuffixRotator().rotate(SecurityActions.getErrorManager(acc, this), file.toPath(), getNextSuffix(), maxBackupIndex);
                // start with new file.
                setFileInternal(file, true);
            } catch (IOException e) {
                reportError("Unable to rotate log file", e, ErrorManager.OPEN_FAILURE);
            }
        }
    }

    private void setFileInternal(final File file, final boolean doPrivileged) throws FileNotFoundException {
        if (System.getSecurityManager() == null || !doPrivileged) {
            super.setFile(file);
            if (outputStream != null) {
                outputStream.currentSize = file == null ? 0L : file.length();
            }
        } else {
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                try {
                    super.setFile(file);
                    if (outputStream != null) {
                        outputStream.currentSize = file == null ? 0L : file.length();
                    }
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }
                return null;
            }, acc);
        }
    }
}
