/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.configuration;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyConfigurationTests {
    private static final String DEFAULT_PATTERN = "%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n";

    private LogContext logContext;

    @Before
    public void setup() {
        logContext = LogContext.create();
        TestHandler.INITIALIZED = false;
        TestFormatter.INITIALIZED = false;
        TestErrorManager.INITIALIZED = false;
        TestFilter.INITIALIZED = false;
    }

    @After
    public void tearDown() throws Exception {
        logContext.close();
    }

    @Test
    public void readConfigs() throws Exception {
        final Path configDir = Paths.get(System.getProperty("config.dir"));
        Assert.assertTrue("Missing config dir: " + configDir, Files.exists(configDir));
        Files.walk(configDir)
                .filter(Files::isRegularFile)
                .forEach((configFile) -> {
                    try (
                            LogContext logContext = LogContext.create();
                            Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                        final Properties properties = new Properties();
                        properties.load(reader);
                        PropertyContextConfiguration.configure(logContext, properties);
                    } catch (Exception e) {
                        final StringWriter writer = new StringWriter();
                        writer.append("Failed to configure ")
                                .append(configFile.getFileName().toString())
                                .append(System.lineSeparator());
                        e.printStackTrace(new PrintWriter(writer));
                        Assert.fail(writer.toString());
                    }
                });
    }

    @Test
    public void testSimple() {
        final Properties config = defaultProperties();
        PropertyContextConfiguration.configure(logContext, config);
        testDefault(2, 1);
    }

    @Test
    public void testLogger() {
        final String loggerName = PropertyConfigurationTests.class.getName();
        final Properties config = defaultProperties(loggerName);

        config.setProperty(String.join(",", "logger", loggerName, "level"), "INFO");
        config.setProperty(String.join(".", "logger", loggerName, "handlers"), "TEST");

        config.setProperty("handler.TEST", TestHandler.class.getName());

        PropertyContextConfiguration.configure(logContext, config);
        testDefault(3, 1);

        final Logger logger = logContext.getLoggerIfExists(loggerName);
        Assert.assertNotNull(logger);
        final TestHandler testHandler = findType(TestHandler.class, logger.getHandlers());
        Assert.assertNotNull("Failed to find TestHandler", testHandler);
        Assert.assertTrue(TestHandler.INITIALIZED);
    }

    @Test
    public void testErrorManager() {
        final Properties config = defaultProperties();
        String handlers = config.getProperty("logger.handlers");
        if (handlers == null) {
            handlers = "TEST";
        } else {
            handlers = handlers + ",TEST";
        }
        config.setProperty("logger.handlers", handlers);

        config.setProperty("handler.TEST", TestHandler.class.getName());
        config.setProperty("handler.TEST.errorManager", "TEST");

        config.setProperty("errorManager.TEST", TestErrorManager.class.getName());

        PropertyContextConfiguration.configure(logContext, config);
        testDefault(2, 2);

        final Logger rootLogger = logContext.getLogger("");
        final TestHandler handler = findType(TestHandler.class, rootLogger.getHandlers());
        Assert.assertNotNull(handler);
        Assert.assertTrue(TestHandler.INITIALIZED);

        final ErrorManager errorManager = handler.getErrorManager();
        Assert.assertTrue(errorManager instanceof TestErrorManager);
        Assert.assertTrue(TestErrorManager.INITIALIZED);
    }

    @Test
    public void testPojo() {
        final Properties config = defaultProperties();
        addPojoConfiguration(config);
        PropertyContextConfiguration.configure(logContext, config);
        testDefault(2, 2);

        final Logger rootLogger = logContext.getLogger("");
        final Handler[] handlers = rootLogger.getHandlers();

        // Find the POJO handler
        final PojoHandler handler = findType(PojoHandler.class, handlers);
        Assert.assertNotNull("POJO handler was not found: " + Arrays.toString(handlers), handler);
        Assert.assertNotNull(handler.pojoObject);
        Assert.assertTrue(handler.pojoObject.initialized);
        Assert.assertEquals("testValue", handler.pojoObject.value);

    }

    @Test
    public void testUnusedHandler() {
        final Properties config = defaultProperties();
        config.setProperty("handler.TEST", TestHandler.class.getName());

        PropertyContextConfiguration.configure(logContext, config);
        testDefault(2, 1);

        // The test handler should not have been activated
        Assert.assertFalse("The handler should not have been initialized", TestHandler.INITIALIZED);
    }

    @Test
    public void testUnusedFormatter() {
        final Properties config = defaultProperties();
        config.setProperty("formatter.TEST", TestFormatter.class.getName());

        PropertyContextConfiguration.configure(logContext, config);
        testDefault(2, 1);

        // The test handler should not have been activated
        Assert.assertFalse("The formatter should not have been initialized", TestFormatter.INITIALIZED);
    }

    @Test
    public void testUnusedErrorManager() {
        final Properties config = defaultProperties();
        config.setProperty("errorManager.TEST", TestErrorManager.class.getName());

        PropertyContextConfiguration.configure(logContext, config);
        testDefault(2, 1);

        // The test handler should not have been activated
        Assert.assertFalse("The error manager should not have been initialized", TestErrorManager.INITIALIZED);
    }

    @Test
    public void testUnusedFilter() {
        final Properties config = defaultProperties();
        config.setProperty("filter.TEST", TestFilter.class.getName());

        PropertyContextConfiguration.configure(logContext, config);
        testDefault(2, 1);

        // The test handler should not have been activated
        Assert.assertFalse("The filter should not have been initialized", TestFilter.INITIALIZED);
    }

    private void testDefault(final int expectedLoggers, final int expectedRootHandlers) {
        final Collection<String> loggerNames = Collections.list(logContext.getLoggerNames());
        // We should have two defined loggers
        Assert.assertEquals("Expected two loggers to be defined found: " + loggerNames, expectedLoggers, loggerNames.size());

        // Test the configured root logger
        final Logger rootLogger = logContext.getLoggerIfExists("");
        Assert.assertNotNull("Root logger was not configured", rootLogger);
        Assert.assertEquals(Level.DEBUG.intValue(), rootLogger.getLevel().intValue());

        // There should only be a console handler, check that it's configured
        final Handler[] handlers = rootLogger.getHandlers();
        Assert.assertNotNull("Expected handles to be defined", handlers);
        Assert.assertEquals(String.format("Expected %d handlers found %d: %s", expectedRootHandlers, handlers.length,
                Arrays.toString(handlers)), expectedRootHandlers, handlers.length);
        final Handler handler = findType(ConsoleHandler.class, handlers);
        Assert.assertNotNull("Failed to find the console handler", handler);
        Assert.assertEquals(ConsoleHandler.class, handler.getClass());
        Assert.assertEquals(Level.TRACE.intValue(), handler.getLevel().intValue());
    }

    private static Properties defaultProperties(final String... additionalLoggers) {
        final Properties config = new Properties();
        // Configure some default loggers
        final StringBuilder sb = new StringBuilder("org.jboss.logmanager.ext");
        for (String additionalLogger : additionalLoggers) {
            sb.append(',').append(additionalLogger);
        }
        config.setProperty("loggers", sb.toString());
        config.setProperty("logger.level", "DEBUG");
        config.setProperty("logger.handlers", "CONSOLE");

        // Configure a handler
        config.setProperty("handler.CONSOLE", ConsoleHandler.class.getName());
        config.setProperty("handler.CONSOLE.level", "TRACE");
        config.setProperty("handler.CONSOLE.formatter", "PATTERN");

        // Configure a formatter
        config.setProperty("formatter.PATTERN", PatternFormatter.class.getName());
        config.setProperty("formatter.PATTERN.properties", "pattern");
        config.setProperty("formatter.PATTERN.pattern", DEFAULT_PATTERN);

        return config;
    }

    private static void addPojoConfiguration(final Properties config) {
        String handlers = config.getProperty("logger.handlers");
        if (handlers == null) {
            handlers = "POJO";
        } else {
            handlers = handlers + ",POJO";
        }
        config.setProperty("logger.handlers", handlers);

        // Configure the POJO handler
        config.setProperty("handler.POJO", PojoHandler.class.getName());
        config.setProperty("handler.POJO.properties", "pojoObject");
        config.setProperty("handler.POJO.pojoObject", "POJO_OBJECT");

        // Configure the POJO object
        config.setProperty("pojos", "POJO_OBJECT");
        config.setProperty("pojo.POJO_OBJECT", PojoObject.class.getName());
        config.setProperty("pojo.POJO_OBJECT.properties", "value");
        config.setProperty("pojo.POJO_OBJECT.value", "testValue");
        config.setProperty("pojo.POJO_OBJECT.postConfiguration", "init,checkInitialized");

    }

    private static <T> T findType(final Class<T> type, final Object[] array) {
        if (array != null) {
            for (Object obj : array) {
                if (obj.getClass().isAssignableFrom(type)) {
                    return type.cast(obj);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    public static class PojoObject {
        String name;
        String value;
        boolean initialized;

        public PojoObject() {
            initialized = false;
        }

        public void init() {
            initialized = true;
        }

        public void checkInitialized() {
            if (!initialized) {
                throw new IllegalStateException("Not initialized");
            }
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }
    }

    @SuppressWarnings("unused")
    public static class PojoHandler extends ExtHandler {
        PojoObject pojoObject;

        public PojoHandler() {
            pojoObject = null;
        }

        @Override
        protected void doPublish(final ExtLogRecord record) {
            super.doPublish(record);
        }

        public PojoObject getPojoObject() {
            return pojoObject;
        }

        public void setPojoObject(final PojoObject pojoObject) {
            this.pojoObject = pojoObject;
        }
    }

    public static class TestHandler extends Handler {
        static boolean INITIALIZED;

        public TestHandler() {
            INITIALIZED = true;
        }

        @Override
        public void publish(final LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }

    public static class TestFormatter extends Formatter {
        static boolean INITIALIZED;

        public TestFormatter() {
            INITIALIZED = true;
        }

        @Override
        public String format(final LogRecord record) {
            return record.getMessage();
        }
    }

    public static class TestErrorManager extends ErrorManager {
        static boolean INITIALIZED;

        public TestErrorManager() {
            INITIALIZED = true;
        }
    }

    public static class TestFilter implements Filter {
        static boolean INITIALIZED;

        public TestFilter() {
            INITIALIZED = true;
        }

        @Override
        public boolean isLoggable(final LogRecord record) {
            return true;
        }
    }
}
