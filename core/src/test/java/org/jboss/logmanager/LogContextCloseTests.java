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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.logmanager.config.ErrorManagerConfiguration;
import org.jboss.logmanager.config.FilterConfiguration;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.jboss.logmanager.config.PojoConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogContextCloseTests {

    @Before
    public void resetTestObjects() {
        TestErrorManager.POJO_OBJECT = null;
        TestFilter.POJO_OBJECT = null;
        TestFormatter.POJO_OBJECT = null;
        TestHandler.ERROR_MANAGER = null;
        TestHandler.FILTER = null;
        TestHandler.FORMATTER = null;
        TestHandler.HANDLERS = null;
        TestHandler.IS_CLOSED = false;
        TestHandler.POJO_OBJECT = null;
    }


    @Test
    public void testCloseLogContext() throws Exception {
        LogContext logContext = LogContext.create();

        // Create a test handler to use
        final TestHandler handler = new TestHandler();
        handler.setErrorManager(new TestErrorManager());
        handler.setFilter(new TestFilter());
        handler.setFormatter(new TestFormatter());
        handler.setLevel(org.jboss.logmanager.Level.TRACE);

        final Logger rootLogger = logContext.getLogger("");
        rootLogger.setLevel(org.jboss.logmanager.Level.WARN);
        final Logger testLogger = logContext.getLogger(LogContextCloseTests.class.getName());
        testLogger.setLevel(Level.FINE);
        final Logger randomLogger = logContext.getLogger(UUID.randomUUID().toString());
        randomLogger.setUseParentFilters(true);

        rootLogger.addHandler(handler);

        logContext.close();

        // Loggers should have no handlers and have been reset
        Assert.assertEquals(Level.INFO, rootLogger.getLevel());
        final Handler[] handlers = randomLogger.getHandlers();
        Assert.assertTrue(handlers == null || handlers.length == 0);

        assertEmptyContext(logContext, rootLogger, testLogger, randomLogger);
    }

    @Test
    public void testCloseLogContextConfiguration() throws Exception {
        final LogContext logContext = LogContext.create();
        final LogContextConfiguration logContextConfiguration = LogContextConfiguration.Factory.create(logContext);

        // Add a POJO to ensure it gets removed
        final PojoConfiguration pojoConfiguration = logContextConfiguration.addPojoConfiguration(null,
                PojoObject.class.getName(), "pojo");

        // Create an error manager
        final ErrorManagerConfiguration errorManagerConfiguration = logContextConfiguration.addErrorManagerConfiguration(null,
                TestErrorManager.class.getName(), "error-manager");
        errorManagerConfiguration.setPropertyValueString("pojoObject", pojoConfiguration.getName());

        // Create a filter
        final FilterConfiguration filterConfiguration = logContextConfiguration.addFilterConfiguration(null,
                TestFilter.class.getName(), "filter");
        filterConfiguration.setPropertyValueString("pojoObject", pojoConfiguration.getName());

        // Create a formatter
        final FormatterConfiguration formatterConfiguration = logContextConfiguration.addFormatterConfiguration(null,
                TestFormatter.class.getName(), "formatter");
        formatterConfiguration.setPropertyValueString("pojoObject", pojoConfiguration.getName());

        // Create a handler
        final HandlerConfiguration handlerConfiguration = logContextConfiguration.addHandlerConfiguration(null,
                TestHandler.class.getName(), "handler");
        handlerConfiguration.setPropertyValueString("pojoObject", pojoConfiguration.getName());
        handlerConfiguration.setFilter(filterConfiguration.getName());
        handlerConfiguration.setErrorManagerName(errorManagerConfiguration.getName());
        handlerConfiguration.setFormatterName(formatterConfiguration.getName());

        // Create the root-logger configuration
        final LoggerConfiguration rootLoggerConfig = logContextConfiguration.addLoggerConfiguration("");
        rootLoggerConfig.setFilter(filterConfiguration.getName());
        rootLoggerConfig.addHandlerName(handlerConfiguration.getName());
        rootLoggerConfig.setLevel("WARN");

        final LoggerConfiguration testLoggerConfig = logContextConfiguration.addLoggerConfiguration(LogContextCloseTests.class.getName());
        testLoggerConfig.setLevel("DEBUG");
        testLoggerConfig.addHandlerName(handlerConfiguration.getName());

        final LoggerConfiguration randomLoggerConfig = logContextConfiguration.addLoggerConfiguration(UUID.randomUUID().toString());
        randomLoggerConfig.setLevel("ERROR");
        randomLoggerConfig.setUseParentHandlers(false);


        logContextConfiguration.commit();

        // Create the loggers on the log context to test they've been reset, note this is required to be done before
        // the context is closed, but after a commit
        final Logger rootLogger = logContext.getLogger(rootLoggerConfig.getName());
        final Logger testLogger = logContext.getLogger(testLoggerConfig.getName());
        final Logger randomLogger = logContext.getLogger(randomLoggerConfig.getName());

        logContext.close();

        assertEmptyNames("error manager", logContextConfiguration.getErrorManagerNames());
        assertEmptyNames("filter", logContextConfiguration.getFilterNames());
        assertEmptyNames("formatter", logContextConfiguration.getFormatterNames());
        assertEmptyNames("handler", logContextConfiguration.getHandlerNames());
        assertEmptyNames("logger", logContextConfiguration.getLoggerNames());
        assertEmptyNames("POJO", logContextConfiguration.getPojoNames());

        assertEmptyContext(logContext, rootLogger, testLogger, randomLogger);
        // The handler is really the only object available for context since it has a close on it
        Assert.assertNull("Expected the handler to be reset.", TestHandler.FORMATTER);

        // Assert the handler itself has been closed
        Assert.assertTrue("The handler was expected to be closed", TestHandler.IS_CLOSED);
    }

    @Test
    public void testCloseWithAttachment() throws Exception {
        LogContext logContext = LogContext.create();
        final Logger.AttachmentKey<String> key = new Logger.AttachmentKey<>();
        final String value = "test value";
        Logger rootLogger = logContext.getLogger("");
        Assert.assertNull(rootLogger.attach(key, value));

        // Close and ensure the context is clean
        logContext.close();
        Assert.assertNull(rootLogger.getAttachment(key));
        assertEmptyContext(logContext, rootLogger);

        // Test attachIfAbsent()
        logContext = LogContext.create();
        rootLogger = logContext.getLogger("");
        Assert.assertNull(rootLogger.attachIfAbsent(key, value));

        // Close and ensure the context is clean
        logContext.close();
        Assert.assertNull(rootLogger.getAttachment(key));
        assertEmptyContext(logContext, rootLogger);

        // Test detach()
        logContext = LogContext.create();
        rootLogger = logContext.getLogger("");
        Assert.assertNull(rootLogger.attach(key, value));
        Assert.assertEquals(value, rootLogger.detach(key));
        logContext.close();
        Assert.assertNull(rootLogger.getAttachment(key));
        assertEmptyContext(logContext, rootLogger);
    }

    private void assertEmptyContext(final LogContext logContext, final Logger... loggers) {
        // Inspect the log context and ensure it's "empty"
        final LoggerNode rootLogger = logContext.getRootLoggerNode();
        final Handler[] handlers = rootLogger.getHandlers();
        Assert.assertTrue("Expected the handlers to be removed.", handlers == null || handlers.length == 0);
        Assert.assertNull("Expected the filter to be null", rootLogger.getFilter());
        Assert.assertEquals("Expected the level to be INFO for logger the root logger", Level.INFO, rootLogger.getLevel());
        Assert.assertFalse("Expected the useParentFilters to be false for the root logger", rootLogger.getUseParentFilters());
        Assert.assertTrue("Expected the useParentHandlers to be true for the root logger", rootLogger.getUseParentHandlers());
        final Collection<LoggerNode> children = rootLogger.getChildren();
        if (!children.isEmpty()) {
            final StringBuilder msg = new StringBuilder("Expected no children to be remaining on the root logger. Remaining loggers: ");
            final Iterator<LoggerNode> iter = children.iterator();
            while (iter.hasNext()) {
                msg.append('\'').append(iter.next().getFullName()).append('\'');
                if (iter.hasNext()) {
                    msg.append(", ");
                }
            }
            Assert.fail(msg.toString());
        }

        for (Logger logger : loggers) {
            assertLoggerReset(logger);
        }
    }

    private void assertLoggerReset(final Logger logger) {
        String loggerName = logger.getName();
        final Level expectedLevel;
        if ("".equals(loggerName)) {
            loggerName = "root";
            expectedLevel = Level.INFO;
        } else {
            expectedLevel = null;
        }
        final Handler[] handlers = logger.getHandlers();
        Assert.assertNull("Expected the filter to be null for logger " + loggerName, logger.getFilter());
        Assert.assertTrue("Empty handlers expected for logger " + loggerName, handlers == null || handlers.length == 0);
        Assert.assertEquals("Expected the level to be " + expectedLevel + " for logger " + loggerName, expectedLevel, logger.getLevel());
        Assert.assertFalse("Expected the useParentFilters to be false for logger " + loggerName, logger.getUseParentFilters());
        Assert.assertTrue("Expected the useParentHandlers to be true for logger " + loggerName, logger.getUseParentHandlers());
    }

    private void assertEmptyNames(final String description, final Collection<String> names) {
        Assert.assertTrue(String.format("The configuration should not have any %s names, but found: %s", description, names),
                names.isEmpty());
    }


    @SuppressWarnings("unused")
    public static class TestFilter implements Filter {
        private static PojoObject POJO_OBJECT;

        @Override
        public boolean isLoggable(final LogRecord record) {
            return true;
        }

        public void setPojoObject(final PojoObject pojoObject) {
            POJO_OBJECT = pojoObject;
        }
    }

    @SuppressWarnings("unused")
    public static class TestFormatter extends Formatter {
        private static PojoObject POJO_OBJECT;

        public void setPojoObject(final PojoObject pojoObject) {
            POJO_OBJECT = pojoObject;
        }

        @Override
        public String format(final LogRecord record) {
            return ExtLogRecord.wrap(record).getFormattedMessage();
        }
    }

    @SuppressWarnings("unused")
    public static class TestErrorManager extends ErrorManager {
        private static PojoObject POJO_OBJECT;

        public void setPojoObject(final PojoObject pojoObject) {
            POJO_OBJECT = pojoObject;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class TestHandler extends ExtHandler {
        private static PojoObject POJO_OBJECT;
        private static Handler[] HANDLERS;
        private static Formatter FORMATTER;
        private static Filter FILTER;
        private static ErrorManager ERROR_MANAGER;
        private static boolean IS_CLOSED;

        public TestHandler() {
            IS_CLOSED = false;
        }

        @Override
        public void close() throws SecurityException {
            // Null out static values
            POJO_OBJECT = null;
            FORMATTER = null;
            HANDLERS = null;
            FORMATTER = null;
            FILTER = null;
            ERROR_MANAGER = null;
            IS_CLOSED = true;
            super.close();
        }

        @Override
        public Handler[] setHandlers(final Handler[] newHandlers) throws SecurityException {
            HANDLERS = Arrays.copyOf(newHandlers, newHandlers.length);
            return super.setHandlers(newHandlers);
        }

        @Override
        public void addHandler(final Handler handler) throws SecurityException {
            if (handler == null) {
                throw new RuntimeException("Cannot add a null handler");
            }
            if (HANDLERS == null) {
                HANDLERS = new Handler[] {handler};
            } else {
                final int len = HANDLERS.length + 1;
                HANDLERS = Arrays.copyOf(HANDLERS, len);
                HANDLERS[len - 1] = handler;
            }
            super.addHandler(handler);
        }

        @Override
        public void removeHandler(final Handler handler) throws SecurityException {
            if (handler == null) {
                throw new RuntimeException("Cannot remove a null handler");
            }
            if (HANDLERS == null) {
                throw new RuntimeException("Attempting to remove a handler that does not exist: " + handler);
            } else {
                if (HANDLERS.length == 1) {
                    HANDLERS = null;
                } else {
                    boolean success = false;
                    final Handler[] newHandlers = new Handler[HANDLERS.length - 1];
                    int newIndex = 0;
                    for (int i = 0; i < HANDLERS.length; i++) {
                        final Handler current = HANDLERS[i];
                        if (!success && i > newHandlers.length) {
                            break;
                        }
                        if (handler != current) {
                            newHandlers[newIndex++] = current;
                        } else {
                            success = true;
                        }
                    }
                    if (!success) {
                        throw new RuntimeException("Failed to remove handler " + handler + " as it did no appear to exist.");
                    }
                }
            }
            super.removeHandler(handler);
        }

        @Override
        public void setFormatter(final Formatter newFormatter) throws SecurityException {
            FORMATTER = newFormatter;
            super.setFormatter(newFormatter);
        }

        @Override
        public void setFilter(final Filter newFilter) throws SecurityException {
            FILTER = newFilter;
            super.setFilter(newFilter);
        }

        @Override
        public void setErrorManager(final ErrorManager em) {
            ERROR_MANAGER = em;
            super.setErrorManager(em);
        }

        @Override
        public void setLevel(final Level newLevel) throws SecurityException {
            super.setLevel(newLevel);
        }

        public void setPojoObject(final PojoObject pojoObject) {
            POJO_OBJECT = pojoObject;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class PojoObject {
    }
}
