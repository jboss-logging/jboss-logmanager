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
import java.util.Deque;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;

/**
 * A handler that queues messages until it's {@linkplain #activate() activated}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class DelayedHandler extends ExtHandler {

    protected final Object outputLock = new Object();

    private final Deque<ExtLogRecord> logRecords = new ArrayDeque<>();

    private volatile boolean activated = false;

    @Override
    public final void publish(final LogRecord record) {
        if (isEnabled() && record != null) {
            publish(ExtLogRecord.wrap(record));
        }
    }

    @Override
    public final void publish(final ExtLogRecord record) {
        if (isEnabled() && record != null) {
            if (activated) {
                doPublish(record);
            } else {
                synchronized (outputLock) {
                    // Check one more time to see if we've been activated before queuing the messages
                    if (activated) {
                        doPublish(record);
                    } else if (isAutoActivate()) {
                        activate();
                        doPublish(record);
                    } else {
                        if (isCallerCalculationRequired()) {
                            record.copyAll();
                        } else {
                            // Disable the caller calculation since it's been determined we won't be using it
                            record.disableCallerCalculation();
                            // Copy the MDC over
                            record.copyMdc();
                            // In case serialization is required
                            record.getFormattedMessage();
                        }
                        logRecords.addLast(record);
                    }
                }
            }
        }
    }

    @Override
    public final void close() throws SecurityException {
        checkAccess(this);
        synchronized (outputLock) {
            activated = false;
        }
        closeResources();
        super.close();
    }

    /**
     * Activates the handler. Once activation is complete queued messages will be published to the handler.
     */
    public final void activate() {
        synchronized (outputLock) {
            // Only activate if required
            if (requiresActivation()) {
                doActivation();
            }
            // Always attempt to drain the queue
            ExtLogRecord record;
            while ((record = logRecords.pollFirst()) != null) {
                if (isEnabled() && isLoggable(record)) {
                    doPublish(record);
                }
            }
            activated = true;
        }
    }

    /**
     * Indicates whether or not this handler has been {@linkplain #activate() activated}.
     *
     * @return {@code true} if the handler has been activated, otherwise {@code false}
     */
    public final boolean isActivated() {
        return activated;
    }

    /**
     * Indicates whether or not this handler should be automatically activated when the first record is written.
     *
     * @return {@code true} if the handler will be automatically activated, otherwise {@code false} if
     * {@link #activate()} is required to be explicitly invoked
     */
    public abstract boolean isAutoActivate();

    /**
     * Handles closing any internal resources.
     */
    protected abstract void closeResources();

    /**
     * Indicates whether or not activation is required. {@linkplain #doActivation() Activation} will only be invoked
     * if {@code true} is returned.
     *
     * @return {@code true} if {@linkplain #doActivation() activation} should be done, otherwise {@code false}
     */
    protected abstract boolean requiresActivation();

    /**
     * Handles activating the handler. Once activation is complete queued messages will be published to the handler.
     */
    protected void doActivation() {
        // do nothing by default
    }
}
