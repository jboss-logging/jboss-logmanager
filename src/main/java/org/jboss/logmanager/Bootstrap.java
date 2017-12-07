/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Filter;
import java.util.logging.Handler;

import org.jboss.logmanager.handlers.FileHandler;

/**
 * A bootstrap helper that queues messages until the {@link #complete()} method is invoked.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Bootstrap {

    private final LoggerNode loggerNode;
    private final Supplier<? extends Handler> handlerSupplier;
    private final BootstrappedLogRecordPublisher publisher;
    private final LogRecordPublisher handoffPublisher;
    private final boolean isCalculateCaller;
    private volatile boolean complete;
    private volatile BootstrapLogTask bootstrapLogTask;

    /**
     * Creates a new bootstrap.
     *
     * @param loggerNode    the logger node to bootstrap
     * @param configuration the configuration
     */
    Bootstrap(final LoggerNode loggerNode, final BootstrapConfiguration configuration) {
        this.loggerNode = loggerNode;
        this.handlerSupplier = configuration.getHandler();
        handoffPublisher = LogRecordPublisher.DEFAULT;
        if (configuration.isBootstrapEnabled()) {
            publisher = new BootstrappedLogRecordPublisher(configuration.getQueueSize());
            loggerNode.setLevel(configuration.getLevel());
            bootstrapLogTask = registerShutdownHook();
            complete = false;
        } else {
            publisher = null;
            complete = true;
        }
        isCalculateCaller = configuration.isCalculateCaller();
    }

    /**
     * Returns the log publisher currently being used.
     *
     * @return the log publisher
     */
    LogRecordPublisher getPublisher() {
        return complete || publisher == null ? handoffPublisher : publisher;
    }

    /**
     * Completes the bootstrapping by pushing queued messages to the {@linkplain LogRecordPublisher#DEFAULT default}
     * publisher then replaces the publisher on all child logger nodes.
     */
    void complete() {
        if (!complete) {
            unregisterShutdownHook();
            synchronized (publisher) {
                publisher.drain();
                complete = true;
            }
        }
    }

    private BootstrapLogTask registerShutdownHook() {
        final BootstrapLogTask task = new BootstrapLogTask();
        if (System.getSecurityManager() == null) {
            Runtime.getRuntime().addShutdownHook(task);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    Runtime.getRuntime().addShutdownHook(task);
                    return null;
                }
            });
        }
        return task;
    }

    private void unregisterShutdownHook() {
        final BootstrapLogTask bootstrapLogTask = this.bootstrapLogTask;
        this.bootstrapLogTask = null;
        if (bootstrapLogTask != null) {
            if (System.getSecurityManager() == null) {
                Runtime.getRuntime().removeShutdownHook(bootstrapLogTask);
            } else {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        Runtime.getRuntime().removeShutdownHook(bootstrapLogTask);
                        return null;
                    }
                });
            }
        }
    }

    private class BootstrapLogTask extends Thread {
        @Override
        public void run() {
            bootstrapLogTask = null;
            final Handler handler = handlerSupplier.get();
            try {
                // Add a handler to bootstrapped logger node
                loggerNode.addHandler(handler);
                if (handler instanceof FileHandler) {
                    StandardOutputStreams.printError("Logging configuration was never committed before the " +
                            "JVM was terminated. Messages were logged to %s.%n", ((FileHandler) handler).getFile().getAbsolutePath());
                } else {
                    StandardOutputStreams.printError("Logging configuration was never committed before the " +
                            "JVM was terminated. Messages will be logged to handler %s.%n", handler);
                }
                // Complete the current configuration by draining the queue and allowing the handoff publisher to be
                // used for any further logged messages
                complete();
            } catch (Exception e) {
                if (publisher != null) {
                    StandardOutputStreams.printError(e, "Failed to configure logging shutdown handler, replaying errors to stderr.");
                    Entry entry;
                    while ((entry = publisher.logRecords.pollFirst()) != null) {
                        StandardOutputStreams.printError(entry.record.getFormattedMessage());
                    }
                }
            }
        }
    }

    private class BootstrappedLogRecordPublisher implements LogRecordPublisher {

        private final Deque<Entry> logRecords;
        private final int queueSize;
        private final AtomicBoolean warnedOverflow;

        private BootstrappedLogRecordPublisher(final int queueSize) {
            logRecords = new ArrayDeque<>(queueSize);
            this.queueSize = queueSize;
            warnedOverflow = new AtomicBoolean(false);
        }

        @Override
        public synchronized void publish(final LoggerNode loggerNode, final ExtLogRecord record) {
            // If a log message was blocked from being published because we were draining the queue, we can just
            // directly send it to the handoff publisher.
            if (complete) {
                handoffPublisher.publish(loggerNode, record);
            } else {
                final ExtLogRecord rec = ExtLogRecord.wrap(record);
                // If the caller calculation is required we need to calculate it, otherwise we need to disable it
                if (isCalculateCaller) {
                    rec.copyAll();
                } else {
                    rec.disableCallerCalculation();
                    rec.copyMdc();
                    rec.getFormattedMessage();
                }
                // Validate we're not going to overflow
                final int currentSize = logRecords.size();
                if (currentSize == queueSize) {
                    logRecords.pollFirst();
                    if (warnedOverflow.compareAndSet(false, true)) {
                        StandardOutputStreams.printError("Log records queued exceed the maximum size of %d. For each " +
                                "new message the earliest message on the queue will be removed.", queueSize);
                    }
                }
                logRecords.addLast(new Entry(loggerNode, record));
            }
        }

        synchronized void drain() {
            Entry entry;
            while ((entry = logRecords.pollFirst()) != null) {
                final LoggerNode loggerNode = entry.loggerNode;
                final ExtLogRecord record = entry.record;
                // Check the per thread log filter, this is generally done in the Logger itself, however we're
                // processing directly with a LoggerNode and need to validate this.
                Filter filter = null;
                final int effectiveLevel = loggerNode.getEffectiveLevel();
                if (!(LogManager.PER_THREAD_LOG_FILTER && (filter = LogManager.getThreadLocalLogFilter()) != null) &&
                        (record.getLevel().intValue() < effectiveLevel || effectiveLevel == Logger.OFF_INT)) {
                    continue;
                }
                if (LogManager.PER_THREAD_LOG_FILTER && filter != null && !filter.isLoggable(record)) {
                    continue;
                }
                // The record must still be loggable if we're going to publish it
                if (loggerNode.isLoggable(record)) {
                    handoffPublisher.publish(loggerNode, record);
                }
            }
        }

    }

    private static class Entry {
        final LoggerNode loggerNode;
        final ExtLogRecord record;

        private Entry(final LoggerNode loggerNode, final ExtLogRecord record) {
            this.loggerNode = loggerNode;
            this.record = record;
        }
    }
}
