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

import java.io.BufferedWriter;
import java.io.Writer;
import java.io.Closeable;
import java.io.Flushable;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtHandler;

/**
 * A handler which writes to any {@code Writer}.
 */
public class WriterHandler extends ExtHandler {

    private volatile boolean checkHeadEncoding = true;
    private volatile boolean checkTailEncoding = true;
    private Writer writer;

    /**
     * Construct a new instance.
     */
    public WriterHandler() {
    }

    /** {@inheritDoc} */
    protected void doPublish(final ExtLogRecord record) {
        final String formatted;
        final Formatter formatter = getFormatter();
        try {
            formatted = formatter.format(record);
        } catch (Exception ex) {
            reportError("Formatting error", ex, ErrorManager.FORMAT_FAILURE);
            return;
        }
        if (formatted.length() == 0) {
            // nothing to write; don't bother
            return;
        }
        try {
            lock.lock();
            try {
                if (writer == null) {
                    return;
                }
                preWrite(record);
                final Writer writer = this.writer;
                if (writer == null) {
                    return;
                }
                writer.write(formatted);
                // only flush if something was written
                super.doPublish(record);
            } finally {
                lock.unlock();
            }
        } catch (Exception ex) {
            reportError("Error writing log message", ex, ErrorManager.WRITE_FAILURE);
            return;
        }
    }

    /**
     * Execute any pre-write policy, such as file rotation.  The write lock is held during this method, so make
     * it quick.  The default implementation does nothing.
     *
     * @param record the record about to be logged
     */
    protected void preWrite(final ExtLogRecord record) {
        // do nothing by default
    }

    /**
     * Set the writer.  The writer will then belong to this handler; when the handler is closed or a new writer is set,
     * this writer will be closed.
     *
     * @param writer the new writer, or {@code null} to disable logging
     */
    public void setWriter(final Writer writer) {
        checkAccess();
        Writer oldWriter = null;
        boolean ok = false;
        try {
            lock.lock();
            try {
                oldWriter = this.writer;
                if (oldWriter != null) {
                    writeTail(oldWriter);
                    safeFlush(oldWriter);
                }
                if (writer != null) {
                    writeHead(this.writer = new BufferedWriter(writer));
                } else {
                    this.writer = null;
                }
                ok = true;
            } finally {
                lock.unlock();
            }
        } finally {
            safeClose(oldWriter);
            if (! ok) safeClose(writer);
        }
    }

    /**
     * Determine whether head encoding checking is turned on.
     *
     * @return {@code true} to check and report head encoding problems, or {@code false} to ignore them
     */
    public boolean isCheckHeadEncoding() {
        return checkHeadEncoding;
    }

    /**
     * Establish whether head encoding checking is turned on.
     *
     * @param checkHeadEncoding {@code true} to check and report head encoding problems, or {@code false} to ignore them
     * @return this handler
     */
    public WriterHandler setCheckHeadEncoding(boolean checkHeadEncoding) {
        this.checkHeadEncoding = checkHeadEncoding;
        return this;
    }

    /**
     * Determine whether tail encoding checking is turned on.
     *
     * @return {@code true} to check and report tail encoding problems, or {@code false} to ignore them
     */
    public boolean isCheckTailEncoding() {
        return checkTailEncoding;
    }

    /**
     * Establish whether tail encoding checking is turned on.
     *
     * @param checkTailEncoding {@code true} to check and report tail encoding problems, or {@code false} to ignore them
     * @return this handler
     */
    public WriterHandler setCheckTailEncoding(boolean checkTailEncoding) {
        this.checkTailEncoding = checkTailEncoding;
        return this;
    }

    private void writeHead(final Writer writer) {
        try {
            final Formatter formatter = getFormatter();
            if (formatter != null) {
                final String head = formatter.getHead(this);
                if (checkHeadEncoding) {
                    if (!getCharset().newEncoder().canEncode(head)) {
                        reportError("Section header cannot be encoded into charset \"" + getCharset().name() + "\"", null, ErrorManager.GENERIC_FAILURE);
                        return;
                    }
                }
                writer.write(head);
            }
        } catch (Exception e) {
            reportError("Error writing section header", e, ErrorManager.WRITE_FAILURE);
        }
    }

    private void writeTail(final Writer writer) {
        try {
            final Formatter formatter = getFormatter();
            if (formatter != null) {
                final String tail = formatter.getTail(this);
                if (checkTailEncoding) {
                    if (!getCharset().newEncoder().canEncode(tail)) {
                        reportError("Section tail cannot be encoded into charset \"" + getCharset().name() + "\"", null, ErrorManager.GENERIC_FAILURE);
                        return;
                    }
                }
                writer.write(tail);
            }
        } catch (Exception ex) {
            reportError("Error writing section tail", ex, ErrorManager.WRITE_FAILURE);
        }
    }

    /**
     * Flush this logger.
     */
    public void flush() {
        // todo - maybe this synch is not really needed... if there's a perf detriment, drop it
        lock.lock();
        try {
            safeFlush(writer);
        } finally {
            lock.unlock();
        }
        super.flush();
    }

    /**
     * Close this logger.
     *
     * @throws SecurityException if the caller does not have sufficient permission
     */
    public void close() throws SecurityException {
        checkAccess();
        setWriter(null);
        super.close();
    }

    /**
     * Safely close the resource, reporting an error if the close fails.
     *
     * @param c the resource
     */
    protected void safeClose(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Exception e) {
            reportError("Error closing resource", e, ErrorManager.CLOSE_FAILURE);
        } catch (Throwable ignored) {}
    }

    void safeFlush(Flushable f) {
        try {
            if (f != null) f.flush();
        } catch (Exception e) {
            reportError("Error on flush", e, ErrorManager.FLUSH_FAILURE);
        } catch (Throwable ignored) {}
    }


    /**
     * Do some operation while holding the output lock.
     *
     * @param arg1 the first argument (usually {@code this})
     * @param arg2 the second argument
     * @param operation the operation to execute (must not be {@code null})
     * @return the result of the operation
     * @param <T> the first argument type
     * @param <U> the second argument type
     * @param <R> the return type
     */
    protected <T, U, R> R doLocked(T arg1, U arg2, BiFunction<T, U, R> operation) {
        lock.lock();
        try {
            return operation.apply(arg1, arg2);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Do some operation while holding the output lock.
     *
     * @param arg the argument (usually {@code this})
     * @param operation the operation to execute (must not be {@code null})
     * @return the result of the operation
     * @param <T> the argument type
     * @param <R> the return type
     */
    protected <T, R> R doLocked(T arg, Function<T, R> operation) {
        return doLocked(operation, arg, Function::apply);
    }

    /**
     * Do some operation while holding the output lock.
     *
     * @param operation the operation to execute (must not be {@code null})
     * @return the result of the operation
     * @param <R> the return type
     */
    protected <R> R doLocked(Supplier<R> operation) {
        return doLocked(operation, Supplier::get);
    }

    /**
     * Determine whether the current thread holds the output lock.
     *
     * @return {@code true} if the current thread holds the output lock, or {@code false} if it does not
     */
    protected boolean currentThreadHoldsLock() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * Await a notification on the output lock.
     *
     * @throws InterruptedException if the operation is interrupted
     */
    protected void awaitLock() throws InterruptedException {
        condition.await();
    }

    /**
     * Await a notification on the output lock for the given amount of time.
     *
     * @throws InterruptedException if the operation is interrupted
     */
    protected void awaitLock(long millis) throws InterruptedException {
        condition.await(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Unblock one thread which is waiting on one of the {@code awaitLock(*)} methods.
     */
    protected void notifyLock() {
        condition.notify();
    }

    /**
     * Unblock all threads which are waiting on one of the {@code awaitLock(*)} methods.
     */
    protected void notifyAllLock() {
        condition.notifyAll();
    }
}
