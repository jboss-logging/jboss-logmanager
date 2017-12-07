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

import java.util.logging.Handler;

/**
 * Provides a way for a {@link LoggerNode} to write messages.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
interface LogRecordPublisher {

    /**
     * A default publisher which writes messages to a logger nodes handlers. This is done recursively if the
     * logger nodes {@link LoggerNode#getUseParentHandlers()} is set to {@code true}.
     */
    LogRecordPublisher DEFAULT = new LogRecordPublisher() {
        @Override
        public void publish(final LoggerNode loggerNode, final ExtLogRecord record) {
            for (Handler handler : loggerNode.getHandlers()) {
                try {
                    handler.publish(record);
                } catch (VirtualMachineError e) {
                    throw e;
                } catch (Throwable t) {
                    StandardOutputStreams.printError(t, "Failed to publish record to handler.");
                }
            }
            if (loggerNode.getUseParentHandlers()) {
                final LoggerNode parent = loggerNode.getParent();
                // Use this publish method to avoid another push to a different publisher which may in turn rerun this
                // publish method as well as avoid the isLoggable() check.
                if (parent != null) publish(parent, record);
            }
        }
    };

    /**
     * Publishes the record to the logger node.
     *
     * @param loggerNode the logger node to publish the record to
     * @param record     the record to publish
     */
    void publish(LoggerNode loggerNode, ExtLogRecord record);
}
