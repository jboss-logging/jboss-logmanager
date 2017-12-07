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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.cpu.ProcessorInfo;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class BootstrapLoggingTests {

    private static final int ITERATIONS = Integer.parseInt(System.getProperty("org.jboss.bootstrap.test.iterations", "1000"));

    @After
    public void cleanup() {
        TestHandler.MESSAGES.clear();
    }

    @Test
    public void testBootstrapConfiguredConfigurationAPI() {
        final LogContext logContext = LogContext.create(BootstrapConfiguration.create(true).useConsoleHandler());

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.info("Test message 1");
        rootLogger.fine("Test message 2");

        final Logger testLogger = logContext.getLogger(BootstrapLoggingTests.class.getName());
        testLogger.warning("Test message 3");

        final Logger randomLogger = logContext.getLogger(UUID.randomUUID().toString());
        randomLogger.severe("Test message 4");

        final LogContextConfiguration logContextConfiguration = LogContextConfiguration.Factory.create(logContext);
        final HandlerConfiguration handlerConfiguration = logContextConfiguration.addHandlerConfiguration(
                null, TestHandler.class.getName(), "test-handler");
        logContextConfiguration.addLoggerConfiguration("").addHandlerName(handlerConfiguration.getName());
        logContextConfiguration.commit();

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
    public void testBootstrapConfigured() {
        final LogContext logContext = LogContext.create(BootstrapConfiguration.create(true).useConsoleHandler().setLogLevel(Level.TRACE));

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.info("Test message 1");
        rootLogger.fine("Test message 2");

        final Logger testLogger = logContext.getLogger(BootstrapLoggingTests.class.getName());
        testLogger.warning("Test message 3");

        final Logger randomLogger = logContext.getLogger(UUID.randomUUID().toString());
        randomLogger.severe("Test message 4");

        final TestHandler handler = new TestHandler();
        rootLogger.addHandler(handler);

        // We're not resetting the root logger level so it should be left at TRACE
        logContext.configurationComplete();

        rootLogger.info("Test message 5");
        testLogger.severe("Test message 6");
        randomLogger.log(Level.TRACE, "Test message 7");

        Assert.assertEquals(7, TestHandler.MESSAGES.size());

        // Test the messages actually logged
        Assert.assertEquals("Test message 1", TestHandler.MESSAGES.get(0).getFormattedMessage());
        Assert.assertEquals("Test message 2", TestHandler.MESSAGES.get(1).getFormattedMessage());
        Assert.assertEquals("Test message 3", TestHandler.MESSAGES.get(2).getFormattedMessage());
        Assert.assertEquals("Test message 4", TestHandler.MESSAGES.get(3).getFormattedMessage());
        Assert.assertEquals("Test message 5", TestHandler.MESSAGES.get(4).getFormattedMessage());
        Assert.assertEquals("Test message 6", TestHandler.MESSAGES.get(5).getFormattedMessage());
        Assert.assertEquals("Test message 7", TestHandler.MESSAGES.get(6).getFormattedMessage());
    }

    @Test
    public void testBootstrapConfiguredLevelNotLogged() {
        final LogContext logContext = LogContext.create(BootstrapConfiguration.create(true).useConsoleHandler().setLogLevel(Level.TRACE));

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.info("Test message 1");
        rootLogger.fine("Test message 2");

        final Logger testLogger = logContext.getLogger(BootstrapLoggingTests.class.getName());
        testLogger.warning("Test message 3");

        final Logger randomLogger = logContext.getLogger(UUID.randomUUID().toString());
        randomLogger.severe("Test message 4");

        final TestHandler handler = new TestHandler();
        rootLogger.addHandler(handler);
        // Set the root logger level to INFO as it will have been set to TRACE in bootstrapping
        rootLogger.setLevel(Level.INFO);
        logContext.configurationComplete();

        rootLogger.info("Test message 5");
        testLogger.severe("Test message 6");
        randomLogger.log(Level.TRACE, "Test message 7");

        // FINE and TRACE levels should not be logged
        Assert.assertEquals(5, TestHandler.MESSAGES.size());

        // Test the messages actually logged
        Assert.assertEquals("Test message 1", TestHandler.MESSAGES.get(0).getFormattedMessage());
        Assert.assertEquals("Test message 3", TestHandler.MESSAGES.get(1).getFormattedMessage());
        Assert.assertEquals("Test message 4", TestHandler.MESSAGES.get(2).getFormattedMessage());
        Assert.assertEquals("Test message 5", TestHandler.MESSAGES.get(3).getFormattedMessage());
        Assert.assertEquals("Test message 6", TestHandler.MESSAGES.get(4).getFormattedMessage());
    }

    @Test
    public void testBootstrapConfiguredLevelLogged() {
        final LogContext logContext = LogContext.create(BootstrapConfiguration.create(true).useConsoleHandler().setLogLevel(Level.TRACE));

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.info("Test message 1");
        rootLogger.fine("Test message 2");

        final Logger testLogger = logContext.getLogger(BootstrapLoggingTests.class.getName());
        testLogger.warning("Test message 3");

        final Logger randomLogger = logContext.getLogger(UUID.randomUUID().toString());
        randomLogger.setLevel(Level.TRACE);
        randomLogger.severe("Test message 4");

        final TestHandler handler = new TestHandler();
        rootLogger.addHandler(handler);
        // Set the root logger level to INFO as it will have been set to TRACE in bootstrapping
        rootLogger.setLevel(Level.INFO);
        logContext.configurationComplete();

        rootLogger.info("Test message 5");
        testLogger.severe("Test message 6");
        randomLogger.log(Level.TRACE, "Test message 7");

        // FINE and TRACE levels should not be logged
        Assert.assertEquals(6, TestHandler.MESSAGES.size());

        // Test the messages actually logged
        Assert.assertEquals("Test message 1", TestHandler.MESSAGES.get(0).getFormattedMessage());
        Assert.assertEquals("Test message 3", TestHandler.MESSAGES.get(1).getFormattedMessage());
        Assert.assertEquals("Test message 4", TestHandler.MESSAGES.get(2).getFormattedMessage());
        Assert.assertEquals("Test message 5", TestHandler.MESSAGES.get(3).getFormattedMessage());
        Assert.assertEquals("Test message 6", TestHandler.MESSAGES.get(4).getFormattedMessage());
        Assert.assertEquals("Test message 7", TestHandler.MESSAGES.get(5).getFormattedMessage());
    }

    @Test
    public void testBootstrapAllLoggedAfterComplete() throws Exception {
        final ExecutorService service = Executors.newFixedThreadPool(ProcessorInfo.availableProcessors() * 2);
        final LogContext logContext = LogContext.create(BootstrapConfiguration.create(true).useConsoleHandler());

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.addHandler(new TestHandler());

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                final int current = i;
                service.submit(() -> rootLogger.info(Integer.toString(current)));
            }
            // Wait until all the messages have been logged before
            service.shutdown();
            service.awaitTermination(20, TimeUnit.SECONDS);
            logContext.configurationComplete();

            // Test that all messages have been flushed to the handler
            Assert.assertEquals(ITERATIONS, TestHandler.MESSAGES.size());

        } finally {
            Assert.assertTrue(service.shutdownNow().isEmpty());
        }
    }

    @Test
    public void testBootstrapAllLoggedMidComplete() throws Exception {
        final ExecutorService service = Executors.newFixedThreadPool(ProcessorInfo.availableProcessors() * 2);
        final LogContext logContext = LogContext.create(BootstrapConfiguration.create(true).useConsoleHandler());

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.addHandler(new TestHandler());
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
            // Wait for a short time to make sure some messages are logged before we commit
            TimeUnit.MILLISECONDS.sleep(150L);
            logContext.configurationComplete();
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
    public void testBootstrapOrder() throws Exception {
        final List<String> expected = new ArrayList<>(ITERATIONS);
        final ExecutorService service = Executors.newFixedThreadPool(ProcessorInfo.availableProcessors() * 2);
        final LogContext logContext = LogContext.create(BootstrapConfiguration.create(true).useConsoleHandler());

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.addHandler(new TestHandler());
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
            logContext.configurationComplete();
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

    public static class TestHandler extends ExtHandler {
        static final List<ExtLogRecord> MESSAGES = new ArrayList<>();

        @Override
        protected synchronized void doPublish(final ExtLogRecord record) {
            super.doPublish(record);
            MESSAGES.add(record);
        }

        @Override
        public void close() throws SecurityException {
            super.close();
            MESSAGES.clear();
        }
    }
}