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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;
import java.util.Queue;

import java.util.logging.Handler;
import java.util.logging.ErrorManager;

public final class AsyncHandler extends ExtHandler {

    private final CopyOnWriteArrayList<Handler> handlers = new CopyOnWriteArrayList<Handler>();
    private final ThreadFactory threadFactory;
    private final Queue<ExtLogRecord> recordQueue;
    private final AsyncThread asyncThread = new AsyncThread();

    private boolean closed;
    private boolean taskRunning;

    private static final int DEFAULT_QUEUE_LENGTH = 512;

    public AsyncHandler(final int queueLength, final ThreadFactory threadFactory) {
        recordQueue = new ArrayQueue<ExtLogRecord>(queueLength);
        this.threadFactory = threadFactory;
    }

    public AsyncHandler(final ThreadFactory threadFactory) {
        this(DEFAULT_QUEUE_LENGTH, threadFactory);
    }

    public AsyncHandler(final int queueLength) {
        this(queueLength, Executors.defaultThreadFactory());
    }

    public AsyncHandler() {
        this(DEFAULT_QUEUE_LENGTH);
    }

    public void addHandler(final Handler handler) {
        checkAccess();
        synchronized (recordQueue) {
            if (closed) {
                throw new IllegalStateException("Handler is closed");
            }
            handlers.add(handler);
        }
    }

    public void removeHandler(final Handler handler) {
        checkAccess();
        synchronized (recordQueue) {
            if (! closed) {
                handlers.remove(handler);
            }
        }
    }

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

    public void flush() {
        for (Handler handler : handlers) {
            handler.flush();
        }
    }

    public void close() throws SecurityException {
        checkAccess();
        closed = true;
        asyncThread.interrupt();
        handlers.clear();
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
            final CopyOnWriteArrayList<Handler> handlers = AsyncHandler.this.handlers;

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
}
