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

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;
import java.util.Queue;

import java.util.logging.Handler;
import java.util.logging.ErrorManager;

/**
 * An asycnhronous log handler which is used to write to a handler or group of handlers which are "slow" or introduce
 * some degree of latency.
 */
public class AsyncHandler extends ExtHandler {

    private final ThreadFactory threadFactory;
    private final Queue<ExtLogRecord> recordQueue;
    private final AsyncThread asyncThread = new AsyncThread();
    private volatile OverflowAction overflowAction = OverflowAction.BLOCK;

    private boolean closed;
    private boolean taskRunning;

    private static final int DEFAULT_QUEUE_LENGTH = 512;

    /**
     * Construct a new instance.
     *
     * @param queueLength the queue length
     * @param threadFactory the thread factory to use to construct the handler thread
     */
    public AsyncHandler(final int queueLength, final ThreadFactory threadFactory) {
        recordQueue = new ArrayQueue<ExtLogRecord>(queueLength);
        this.threadFactory = threadFactory;
    }

    /**
     * Construct a new instance.
     *
     * @param threadFactory the thread factory to use to construct the handler thread
     */
    public AsyncHandler(final ThreadFactory threadFactory) {
        this(DEFAULT_QUEUE_LENGTH, threadFactory);
    }

    /**
     * Construct a new instance.
     *
     * @param queueLength the queue length
     */
    public AsyncHandler(final int queueLength) {
        this(queueLength, Executors.defaultThreadFactory());
    }

    /**
     * Construct a new instance.
     */
    public AsyncHandler() {
        this(DEFAULT_QUEUE_LENGTH);
    }

    /**
     * Get the overflow action.
     *
     * @return the overflow action
     */
    public OverflowAction getOverflowAction() {
        return overflowAction;
    }

    /**
     * Set the overflow action.
     *
     * @param overflowAction the overflow action
     */
    public void setOverflowAction(final OverflowAction overflowAction) {
        if (overflowAction == null) {
            throw new NullPointerException("overflowAction is null");
        }
        checkAccess();
        this.overflowAction = overflowAction;
    }

    /** {@inheritDoc} */
    public void publish(final ExtLogRecord record) {
        final Queue<ExtLogRecord> recordQueue = this.recordQueue;
        boolean intr = Thread.interrupted();
        // prepare record to move to another thread
        record.copyAll();
        try {
            synchronized (recordQueue) {
                if (closed) {
                    return;
                }
                startTaskIfNotRunning();
                while (! recordQueue.offer(record)) {
                    try {
                        if (overflowAction == OverflowAction.DISCARD) {
                            return;
                        }
                        recordQueue.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                    if (closed) {
                        return;
                    }
                    startTaskIfNotRunning();
                }
                recordQueue.notify();
            }
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    private void startTaskIfNotRunning() {
        if (! taskRunning) {
            taskRunning = true;
            threadFactory.newThread(asyncThread).start();
        }
    }

    /** {@inheritDoc} */
    public void flush() {
        for (Handler handler : handlers) {
            handler.flush();
        }
    }

    /** {@inheritDoc} */
    public void close() throws SecurityException {
        checkAccess();
        closed = true;
        asyncThread.interrupt();
        clearHandlers();
    }

    private final class AsyncThread implements Runnable {
        private volatile Thread thread;

        void interrupt() {
            final Thread thread = this.thread;
            if (thread != null) {
                thread.interrupt();
            }
        }

        public void run() {
            thread = Thread.currentThread();
            final Queue<ExtLogRecord> recordQueue = AsyncHandler.this.recordQueue;
            final Handler[] handlers = AsyncHandler.this.handlers;

            boolean intr = false;
            try {
                for (;;) {
                    ExtLogRecord rec;
                    synchronized (recordQueue) {
                        while ((rec = recordQueue.poll()) == null) {
                            if (closed) {
                                return;
                            }
                            try {
                                recordQueue.wait();
                            } catch (InterruptedException e) {
                                intr = true;
                            }
                        }
                        recordQueue.notify();
                    }
                    for (Handler handler : handlers) try {
                        handler.publish(rec);
                    } catch (Exception e) {
                        getErrorManager().error("Publication error", e, ErrorManager.WRITE_FAILURE);
                    } catch (VirtualMachineError e) {
                        throw e;
                    } catch (Throwable t) {
                        // ignore :-/
                    }
                }
            } finally {
                synchronized (recordQueue) {
                    taskRunning = false;
                    recordQueue.notify();
                }
                thread = null;
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public enum OverflowAction {
        BLOCK,
        DISCARD,
    }
}
