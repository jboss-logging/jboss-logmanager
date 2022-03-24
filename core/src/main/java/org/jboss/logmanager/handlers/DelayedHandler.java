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

package org.jboss.logmanager.handlers;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.StandardOutputStreams;
import org.jboss.logmanager.formatters.PatternFormatter;

/**
 * A handler that queues messages until it's at least one child handler is {@linkplain #addHandler(Handler) added} or
 * {@linkplain #setHandlers(Handler[]) set}. If the children handlers are {@linkplain #clearHandlers() cleared} then
 * the handler is no longer considered activated and messages will once again be queued.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class DelayedHandler extends ExtHandler {

    private final Map<java.util.logging.Level, Deque<ExtLogRecord>> queues = new HashMap<>();

    private volatile boolean activated = false;
    private volatile boolean callerCalculationRequired = false;

    private final LogContext logContext;
    private final int queueLimit;
    private final java.util.logging.Level warnThreshold;

    /**
     * Construct a new instance.
     */
    public DelayedHandler() {
        this(null);
    }

    /**
     * Construct a new instance, with the given log context used to recheck log levels on replay.
     *
     * @param logContext the log context to use for level checks on replay, or {@code null} for none
     */
    public DelayedHandler(LogContext logContext) {
        this(logContext, 200);
    }

    /**
     * Construct a new instance.
     * The given queue limit value is used to limit the length of each level queue.
     *
     * @param queueLimit the queue limit
     */
    public DelayedHandler(int queueLimit) {
        this(null, queueLimit);
    }

    /**
     * Construct a new instance, with the given log context used to recheck log levels on replay.
     * The given queue limit value is used to limit the length of each level queue.
     *
     * @param logContext the log context to use for level checks on replay, or {@code null} for none
     * @param queueLimit the queue limit
     */
    public DelayedHandler(LogContext logContext, int queueLimit) {
        this(logContext, queueLimit, Level.INFO);
    }

    /**
     * Construct a new instance.
     * The given queue limit value is used to limit the length of each level queue.
     * The warning threshold specifies that only queues with the threshold level or higher will report overrun errors.
     *
     * @param queueLimit the queue limit
     * @param warnThreshold the threshold level to report queue overruns for
     */
    public DelayedHandler(int queueLimit, Level warnThreshold) {
        this(null, queueLimit, warnThreshold);
    }

    /**
     * Construct a new instance, with the given log context used to recheck log levels on replay.
     * The given queue limit value is used to limit the length of each level queue.
     * The warning threshold specifies that only queues with the threshold level or higher will report overrun errors.
     *
     * @param logContext the log context to use for level checks on replay, or {@code null} for none
     * @param queueLimit the queue limit
     * @param warnThreshold the threshold level to report queue overruns for
     */
    public DelayedHandler(LogContext logContext, int queueLimit, Level warnThreshold) {
        this.logContext = logContext;
        this.queueLimit = queueLimit;
        this.warnThreshold = warnThreshold;
    }

    private static <E> Deque<E> newDeque(Object ignored) {
        return new ArrayDeque<>();
    }

    @Override
    protected void doPublish(final ExtLogRecord record) {
        // If activated just delegate
        if (activated) {
            publishToChildren(record);
            super.doPublish(record);
        } else {
            synchronized (this) {
                // Check one more time to see if we've been activated before queuing the messages
                if (activated) {
                    publishToChildren(record);
                    super.doPublish(record);
                } else {
                    // Determine if we need to calculate the caller information before we queue the record
                    if (isCallerCalculationRequired()) {
                        // prepare record to move to another thread
                        record.copyAll();
                    } else {
                        // Disable the caller calculation since it's been determined we won't be using it
                        record.disableCallerCalculation();
                        // Copy the MDC over
                        record.copyMdc();
                    }
                    Level level = record.getLevel();
                    Deque<ExtLogRecord> q = queues.computeIfAbsent(level, DelayedHandler::newDeque);
                    if (q.size() >= queueLimit && level.intValue() >= warnThreshold.intValue()) {
                        reportError("The delayed handler's queue was overrun and log record(s) were lost. Did you forget to configure logging?", null, ErrorManager.WRITE_FAILURE);
                    }
                    enqueueOrdered(q, record);
                }
            }
        }
    }

    /**
     * Enqueue the log record such that the queue's order (by sequence number) is maintained.
     *
     * @param q the queue
     * @param record the record
     */
    private void enqueueOrdered(Deque<ExtLogRecord> q, ExtLogRecord record) {
        assert Thread.holdsLock(this);
        ExtLogRecord last = q.peekLast();
        if (last != null) {
            // check the ordering
            if (Long.compareUnsigned(last.getSequenceNumber(), record.getSequenceNumber()) > 0) {
                // out of order; we have to re-sort.. typically, it's only going to be out of order by a couple though
                q.pollLast();
                try {
                    enqueueOrdered(q, record);
                } finally {
                    q.addLast(last);
                }
                return;
            }
        }
        // order is OK
        q.addLast(record);
    }

    private Supplier<ExtLogRecord> drain() {
        assert Thread.holdsLock(this);
        if (queues.isEmpty()) {
            return () -> null;
        }
        List<Deque<ExtLogRecord>> values = List.copyOf(queues.values());
        queues.clear();
        int size = values.size();
        List<ExtLogRecord> current = Arrays.asList(new ExtLogRecord[size]);
        // every queue must have at least one item in it
        int i = 0;
        for (Deque<ExtLogRecord> value : values) {
            current.set(i++, value.removeFirst());
        }
        return new Supplier<ExtLogRecord>() {
            @Override
            public ExtLogRecord get() {
                ExtLogRecord min = null;
                int minIdx = 0;
                for (int i = 0; i < size; i ++) {
                    ExtLogRecord item = current.get(i);
                    if (compareSeq(min, item) > 0) {
                        min = item;
                        minIdx = i;
                    }
                }
                if (min == null) {
                    return null;
                }
                current.set(minIdx, values.get(minIdx).pollFirst());
                return min;
            }

            private int compareSeq(ExtLogRecord min, ExtLogRecord testItem) {
                if (min == null) {
                    // null is greater than everything
                    return testItem == null ? 0 : 1;
                } else if (testItem == null) {
                    return -1;
                } else {
                    return Long.compareUnsigned(min.getSequenceNumber(), testItem.getSequenceNumber());
                }
            }
        };
    }

    @Override
    public final void close() throws SecurityException {
        checkAccess();
        synchronized (this) {
            if (!queues.isEmpty()) {
                Formatter formatter = getFormatter();
                if (formatter == null) {
                    formatter = new PatternFormatter("%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n");
                }
                StandardOutputStreams.printError("The DelayedHandler was closed before any children handlers were " +
                        "configured. Messages will be written to stderr.");
                // Always attempt to drain the queue
                Supplier<ExtLogRecord> drain = drain();
                ExtLogRecord record;
                while ((record = drain.get()) != null) {
                    StandardOutputStreams.printError(formatter.format(record));
                }
            }
        }
        activated = false;
        super.close();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that once this is invoked the handler will be activated and the messages will no longer be queued. If more
     * than one child handler is required the {@link #setHandlers(Handler[])} should be used.
     * </p>
     *
     * @see #setHandlers(Handler[])
     */
    @Override
    public void addHandler(final Handler handler) throws SecurityException {
        super.addHandler(handler);
        activate();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that once this is invoked the handler will be activated and the messages will no longer be queued.
     * </p>
     */
    @Override
    public Handler[] setHandlers(final Handler[] newHandlers) throws SecurityException {
        final Handler[] result = super.setHandlers(newHandlers);
        activate();
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that if the last child handler is removed the handler will no longer be activated and the messages will
     * again be queued.
     * </p>
     *
     * @see #clearHandlers()
     */
    @Override
    public void removeHandler(final Handler handler) throws SecurityException {
        super.removeHandler(handler);
        activated = handlers.length != 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that once this is invoked the handler will no longer be activated and messages will again be queued.
     * </p>
     *
     * @see #removeHandler(Handler)
     */
    @Override
    public Handler[] clearHandlers() throws SecurityException {
        activated = false;
        return super.clearHandlers();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This can be overridden to always require the caller calculation by setting the
     * {@link #setCallerCalculationRequired(boolean)} value to {@code true}.
     * </p>
     *
     * @see #setCallerCalculationRequired(boolean)
     */
    @Override
    public boolean isCallerCalculationRequired() {
        return callerCalculationRequired || super.isCallerCalculationRequired();
    }

    /**
     * Sets whether or not {@linkplain ExtLogRecord#copyAll() caller information} will be required when formatting
     * records.
     * <p>
     * If set to {@code true} the {@linkplain ExtLogRecord#copyAll() caller information} will be calculated for each
     * record that is placed in the queue. A value of {@code false} means the
     * {@link super#isCallerCalculationRequired()} will be used.
     * </p>
     * <p>
     * Note that the caller information is only attempted to be calculated when the handler has not been activated. Once
     * activated it's up to the {@linkplain #getHandlers() children handlers} to determine how the record is processed.
     * </p>
     *
     * @param callerCalculationRequired {@code true} if the {@linkplain ExtLogRecord#copyAll() caller information}
     *                                  should always be calculated before the record is being placed in the queue
     */
    public void setCallerCalculationRequired(final boolean callerCalculationRequired) {
        this.callerCalculationRequired = callerCalculationRequired;
    }

    /**
     * Indicates whether or not this handler has been activated.
     *
     * @return {@code true} if the handler has been activated, otherwise {@code false}
     */
    public final boolean isActivated() {
        return activated;
    }

    private synchronized void activate() {
        // Always attempt to drain the queue
        ExtLogRecord record;
        final LogContext logContext = this.logContext;
        Supplier<ExtLogRecord> drain = drain();
        while ((record = drain.get()) != null) {
            if (isEnabled() && isLoggable(record) && (logContext == null || logContext.getLogger(record.getLoggerName()).isLoggable(record.getLevel()))) {
                publishToChildren(record);
            }
        }
        activated = true;
    }

    private void publishToChildren(final ExtLogRecord record) {
        for (Handler handler : handlers) {
            handler.publish(record);
        }
    }
}