/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.routing;

import java.util.regex.Pattern;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.handlers.QueueHandler;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogRouterTests {

    private static final LogContext DEFAULT_LOG_CONTEXT = LogContext.create();
    private static final Logger LOGGER = DEFAULT_LOG_CONTEXT.getLogger(LogRouterTests.class.getName());
    private static final TestLoggerRouter ROUTER = new TestLoggerRouter(DEFAULT_LOG_CONTEXT);

    private static final Level[] LEVELS = {
            Level.TRACE,
            Level.DEBUG,
            Level.INFO,
            Level.WARN,
            Level.ERROR,
            Level.FATAL
    };

    @BeforeClass
    public static void setup() {
        Logger.setLoggerRouter(ROUTER);
    }

    @AfterClass
    public static void cleanup() {
        Logger.setLoggerRouter(null);
    }

    @Test
    public void testHandlerRouting() throws Exception {
        // Create two handlers for the routing
        final QueueHandler handler1 = new QueueHandler();
        final QueueHandler handler2 = new QueueHandler();

        // Create a two separate log contexts
        final LogContext context1 = LogContext.create();
        final LogContext context2 = LogContext.create();

        // Add a handler for the static logger on each log context
        try (AutoCloseable ignored = ROUTER.push(context1)) {
            LOGGER.addHandler(handler1);
            LOGGER.setLevel(Level.ALL);
        }
        try (AutoCloseable ignored = ROUTER.push(context2)) {
            LOGGER.addHandler(handler2);
            LOGGER.setLevel(Level.ALL);
        }

        // Log some messages on each context
        try (AutoCloseable ignored = ROUTER.push(context1)) {
            doLog(1);
        }
        try (AutoCloseable ignored = ROUTER.push(context2)) {
            doLog(2);
        }

        // handler1 should only contain messages from context1
        final ExtLogRecord[] records1 = handler1.getQueue();
        Assert.assertNotNull("Expected handler1 messages to not be null", records1);
        Assert.assertEquals(LEVELS.length, records1.length);
        Pattern pattern = Pattern.compile("(Log [A-Z]{4,5} for 1)");
        for (ExtLogRecord record : records1) {
            final String found = record.getMessage();
            Assert.assertTrue(String.format("Record \"%s\" did not match pattern \"%s\"", found, pattern.pattern()),
                    pattern.matcher(found).matches());
        }

        // handler2 should only contain messages from context2
        final ExtLogRecord[] records2 = handler2.getQueue();
        Assert.assertNotNull("Expected handler2  messages to not be null", records2);
        Assert.assertEquals(LEVELS.length, records2.length);
        pattern = Pattern.compile("(Log [A-Z]{4,5} for 2)");
        for (ExtLogRecord record : records2) {
            final String found = record.getMessage();
            Assert.assertTrue(String.format("Record \"%s\" did not match pattern \"%s\"", found, pattern.pattern()),
                    pattern.matcher(found).matches());
        }
    }

    @Test
    public void testLoggerNames() throws Exception {
        // Create a two separate log contexts
        final LogContext context1 = LogContext.create();
        final LogContext context2 = LogContext.create();

        // Set levels on the logger which should create them on the context
        try (AutoCloseable ignored = ROUTER.push(context1)) {
            LOGGER.setLevel(Level.DEBUG);
        }
        try (AutoCloseable ignored = ROUTER.push(context2)) {
            LOGGER.setLevel(Level.TRACE);
        }

        // The logger should exist on each context
        try (AutoCloseable ignored = ROUTER.push(context1)) {
            Assert.assertNotNull("Missing the logger on context1", context1.getLoggerIfExists(LOGGER.getName()));
            Assert.assertEquals(Level.DEBUG, LOGGER.getLevel());
        }
        try (AutoCloseable ignored = ROUTER.push(context2)) {
            Assert.assertNotNull("Missing the logger on context2", context1.getLoggerIfExists(LOGGER.getName()));
            Assert.assertEquals(Level.TRACE, LOGGER.getLevel());
        }
        Assert.assertNotNull("Missing the logger on the default", DEFAULT_LOG_CONTEXT.getLoggerIfExists(LOGGER.getName()));
        Assert.assertNull("The default logging level should be null", LOGGER.getLevel());
    }

    private static void doLog(final int id) {
        for (Level level : LEVELS) {
            LOGGER.log(level, String.format("Log %s for %d", level, id));
        }
    }

}
