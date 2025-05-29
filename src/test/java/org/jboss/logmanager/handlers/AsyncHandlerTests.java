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

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.MDC;
import org.jboss.logmanager.NDC;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.AsyncHandler.OverflowAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AsyncHandlerTests {

    private BlockingQueueHandler handler;
    private AsyncHandler asyncHandler;

    @BeforeEach
    public void setup() {
        handler = new BlockingQueueHandler();

        asyncHandler = new AsyncHandler();
        asyncHandler.setOverflowAction(OverflowAction.BLOCK);
        asyncHandler.addHandler(handler);
    }

    @AfterEach
    public void tearDown() {
        asyncHandler.close();
        handler.close();
        NDC.clear();
        MDC.clear();
    }

    @Test
    public void testNdc() throws Exception {
        handler.setFormatter(new PatternFormatter("%x"));
        String ndcValue = "Test NDC value";
        NDC.push(ndcValue);
        asyncHandler.doPublish(createRecord());
        Assertions.assertEquals(ndcValue, NDC.pop());
        Assertions.assertEquals(ndcValue, handler.getFirst());

        // Next value should be blank
        asyncHandler.doPublish(createRecord());
        Assertions.assertEquals("", handler.getFirst());

        ndcValue = "New test NDC value";
        NDC.push(ndcValue);
        asyncHandler.doPublish(createRecord());
        NDC.push("invalid");
        Assertions.assertEquals(ndcValue, handler.getFirst());
    }

    @Test
    public void testMdc() throws Exception {
        handler.setFormatter(new PatternFormatter("%X{key}"));
        String mdcValue = "Test MDC value";
        MDC.put("key", mdcValue);
        asyncHandler.doPublish(createRecord());
        MDC.remove("key");
        Assertions.assertEquals(mdcValue, handler.getFirst());

        asyncHandler.doPublish(createRecord());
        Assertions.assertEquals("", handler.getFirst());

        mdcValue = "New test MDC value";
        MDC.put("key", mdcValue);
        asyncHandler.doPublish(createRecord());
        MDC.put("key", "invalid");
        Assertions.assertEquals(mdcValue, handler.getFirst());
    }

    @Test
    public void reentry() throws Exception {
        final ExtHandler reLogHandler = new ExtHandler() {
            private final ThreadLocal<Boolean> entered = ThreadLocal.withInitial(() -> false);

            @Override
            protected void doPublish(final ExtLogRecord record) {
                if (entered.get()) {
                    return;
                }
                try {
                    entered.set(true);
                    super.doPublish(record);
                    // Create a new record and act is if this was through a logger
                    asyncHandler.publish(createRecord());
                } finally {
                    entered.set(false);
                }
            }
        };
        handler.addHandler(reLogHandler);
        handler.setFormatter(new PatternFormatter("%s"));
        asyncHandler.publish(createRecord());
        // We should end up with just one message, since reentrancy is forbidden
        Assertions.assertEquals("Test message", handler.getFirst());
        // This should time out. Then we end up with a null value. We could instead sleep for a shorter time and check
        // if the queue is empty. However, a 5 second delay does not seem too long.
        Assertions.assertNull(handler.getFirst(), () -> "Expected no more entries, but found " + handler.queue);
    }

    static ExtLogRecord createRecord() {
        return new ExtLogRecord(Level.INFO, "Test message", AsyncHandlerTests.class.getName());
    }

    static class BlockingQueueHandler extends ExtHandler {
        private final BlockingDeque<String> queue;

        BlockingQueueHandler() {
            queue = new LinkedBlockingDeque<>();
            setErrorManager(AssertingErrorManager.of());
        }

        @Override
        protected void doPublish(final ExtLogRecord record) {
            queue.addLast(getFormatter().format(record));
            publishToNestedHandlers(record);
            super.doPublish(record);
        }

        String getFirst() throws InterruptedException {
            return queue.pollFirst(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws SecurityException {
            queue.clear();
        }
    }
}
