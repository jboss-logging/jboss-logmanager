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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.cpu.ProcessorInfo;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DelayedHandlerTests {

    private static final int ITERATIONS = Integer.parseInt(System.getProperty("org.jboss.bootstrap.test.iterations", "1000"));

    @After
    public void cleanup() {
        TestHandler.MESSAGES.clear();
        TestHandler.ACTIVATION_COUNT.set(0);
    }

    @Test
    public void testQueuedMessages() {
        final LogContext logContext = LogContext.create();

        final LogContextConfiguration logContextConfiguration = LogContextConfiguration.Factory.create(logContext);
        final HandlerConfiguration handlerConfiguration = logContextConfiguration.addHandlerConfiguration(
                null, TestHandler.class.getName(), "test-handler");
        logContextConfiguration.addLoggerConfiguration("").addHandlerName(handlerConfiguration.getName());
        logContextConfiguration.commit();

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.info("Test message 1");
        rootLogger.fine("Test message 2");

        final Logger testLogger = logContext.getLogger(DelayedHandlerTests.class.getName());
        testLogger.warning("Test message 3");

        final Logger randomLogger = logContext.getLogger("org.jboss.logmanager." + UUID.randomUUID());
        randomLogger.severe("Test message 4");

        // Activate after some messages have been logged
        logContextConfiguration.activate();

        rootLogger.info("Test message 5");
        testLogger.severe("Test message 6");
        randomLogger.finest("Test message 7");

        // The default root logger message is INFO so FINE and FINEST should not be logged
        Assert.assertEquals(5, TestHandler.MESSAGES.size());

        // Test the messages actually logged
        Assert.assertEquals("Test message 1", TestHandler.MESSAGES.get(0).getFormattedMessage());
        Assert.assertEquals("Test message 3", TestHandler.MESSAGES.get(1).getFormattedMessage());
        Assert.assertEquals("Test message 4", TestHandler.MESSAGES.get(2).getFormattedMessage());
        Assert.assertEquals("Test message 5", TestHandler.MESSAGES.get(3).getFormattedMessage());
        Assert.assertEquals("Test message 6", TestHandler.MESSAGES.get(4).getFormattedMessage());
    }


    @Test
    public void testAllLoggedAfterActivation() throws Exception {
        final ExecutorService service = createExecutor();
        final LogContext logContext = LogContext.create();

        final TestHandler handler = new TestHandler();
        handler.setAutoActivate(false);
        final Logger rootLogger = logContext.getLogger("");
        rootLogger.addHandler(handler);

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                final int current = i;
                service.submit(() -> rootLogger.info(Integer.toString(current)));
            }
            // Wait until all the messages have been logged before
            service.shutdown();
            service.awaitTermination(5, TimeUnit.SECONDS);
            handler.activate();

            // Test that all messages have been flushed to the handler
            Assert.assertEquals(ITERATIONS, TestHandler.MESSAGES.size());

            // Test that all messages have been flushed to the handler
            final List<Integer> missing = new ArrayList<>(ITERATIONS);
            for (int i = 0; i < ITERATIONS; i++) {
                missing.add(i);
            }

            final List<Integer> ints = TestHandler.MESSAGES.stream()
                    .map(extLogRecord -> Integer.parseInt(extLogRecord.getFormattedMessage()))
                    .collect(Collectors.toList());
            missing.removeAll(ints);
            Collections.sort(missing);
            Assert.assertEquals(String.format("Missing the following entries: %s", missing), ITERATIONS, TestHandler.MESSAGES.size());

        } finally {
            Assert.assertTrue(service.shutdownNow().isEmpty());
        }
    }

    @Test
    public void testAllLoggedMidActivation() throws Exception {
        final ExecutorService service = createExecutor();
        final LogContext logContext = LogContext.create();

        final TestHandler handler = new TestHandler();
        handler.setAutoActivate(false);
        final Logger rootLogger = logContext.getLogger("");
        rootLogger.addHandler(handler);
        final Random r = new Random();

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                final int current = i;
                service.submit(() -> {
                    TimeUnit.MILLISECONDS.sleep(r.nextInt(15));
                    rootLogger.info(Integer.toString(current));
                    return null;
                });
            }
            // Wait for a short time to make sure some messages are logged before we activate
            TimeUnit.MILLISECONDS.sleep(150L);
            handler.activate();
            // Wait until all the messages have been logged before
            service.shutdown();
            service.awaitTermination(5, TimeUnit.SECONDS);

            // Test that all messages have been flushed to the handler
            final List<Integer> missing = new ArrayList<>(ITERATIONS);
            for (int i = 0; i < ITERATIONS; i++) {
                missing.add(i);
            }

            final List<Integer> ints = TestHandler.MESSAGES.stream()
                    .map(extLogRecord -> Integer.parseInt(extLogRecord.getFormattedMessage()))
                    .collect(Collectors.toList());
            missing.removeAll(ints);
            Collections.sort(missing);
            Assert.assertEquals(String.format("Missing the following entries: %s", missing), ITERATIONS, TestHandler.MESSAGES.size());

        } finally {
            Assert.assertTrue(service.shutdownNow().isEmpty());
        }
    }

    @Test
    public void testOrder() throws Exception {
        final List<String> expected = new ArrayList<>(ITERATIONS);
        final ExecutorService service = createExecutor();
        final LogContext logContext = LogContext.create();

        final TestHandler handler = new TestHandler();
        handler.setAutoActivate(false);
        final Logger rootLogger = logContext.getLogger("");
        rootLogger.addHandler(handler);
        final Random r = new Random();

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                final int current = i;
                service.submit(() -> {
                    TimeUnit.MILLISECONDS.sleep(r.nextInt(15));
                    final String msg = Integer.toString(current);
                    // Need to synchronize to ensure order of the logged messages and the expected messages
                    synchronized (expected) {
                        expected.add(msg);
                        rootLogger.info(msg);
                    }
                    return null;
                });
            }
            // Wait for a short time to make sure some messages are logged before we commit
            TimeUnit.MILLISECONDS.sleep(150L);
            handler.activate();
            // Wait until all the messages have been logged before
            service.shutdown();
            service.awaitTermination(5, TimeUnit.SECONDS);

            // Get the current messages from the handler
            final List<String> found = TestHandler.MESSAGES.stream()
                    .map(ExtLogRecord::getFormattedMessage)
                    .collect(Collectors.toList());
            final List<String> missing = new ArrayList<>(expected);
            missing.removeAll(found);
            Assert.assertTrue("Missing the following entries: " + missing, missing.isEmpty());

            // This shouldn't happen as the above should find it, but it's better to fail here than below.
            Assert.assertEquals(expected.size(), TestHandler.MESSAGES.size());

            // Now we need to test the order of what we have vs what is expected. These should be in the same order
            for (int i = 0; i < expected.size(); i++) {
                final String expectedMessage = expected.get(i);
                final ExtLogRecord record = TestHandler.MESSAGES.get(i);
                Assert.assertEquals(expectedMessage, record.getFormattedMessage());
            }

        } finally {
            Assert.assertTrue(service.shutdownNow().isEmpty());
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(ProcessorInfo.availableProcessors() * 2);
    }

    public static class TestHandler extends DelayedHandler {
        static final List<ExtLogRecord> MESSAGES = new ArrayList<>();
        static final AtomicInteger ACTIVATION_COUNT = new AtomicInteger();

        private boolean autoActivate;

        @SuppressWarnings("WeakerAccess")
        public TestHandler() {
            autoActivate = true;
        }

        @Override
        protected synchronized void doPublish(final ExtLogRecord record) {
            MESSAGES.add(record);
        }

        @Override
        protected void closeResources() throws SecurityException {
            MESSAGES.clear();
            ACTIVATION_COUNT.set(0);
        }

        @Override
        protected boolean requiresActivation() {
            return true;
        }

        @Override
        protected void doActivation() {
            ACTIVATION_COUNT.incrementAndGet();
        }

        @Override
        public boolean isAutoActivate() {
            synchronized (outputLock) {
                return autoActivate;
            }
        }

        void setAutoActivate(final boolean autoActivate) {
            checkAccess(this);
            synchronized (outputLock) {
                this.autoActivate = autoActivate;
            }
        }
    }
}