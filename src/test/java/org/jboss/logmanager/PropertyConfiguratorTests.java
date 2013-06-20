/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.logmanager;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Formatter;

import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.jboss.logmanager.config.PojoConfiguration;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyConfiguratorTests {

    private static final String ENCODING = "UTF-8";

    static {
        System.setProperty("default.log.level", "DEBUG");
    }

    @Test
    public void testReadWrite() throws Exception {
        final Properties defaultProperties = defaultProperties();
        final LogContext logContext = LogContext.create();
        final PropertyConfigurator configurator = new PropertyConfigurator(logContext);
        configurator.configure(defaultProperties);

        // Write out the configuration
        final ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
        defaultProperties.store(new OutputStreamWriter(propsOut, ENCODING), null);
        final ByteArrayOutputStream configOut = new ByteArrayOutputStream();
        configurator.writeConfiguration(configOut);

        // Reload output streams into properties
        final Properties configProps = new Properties();
        final ByteArrayInputStream configIn = new ByteArrayInputStream(configOut.toByteArray());
        configProps.load(new InputStreamReader(configIn, ENCODING));
        final Properties dftProps = new Properties();
        dftProps.load(new InputStreamReader(new ByteArrayInputStream(propsOut.toByteArray()), ENCODING));
        compare(dftProps, configProps);

        // Reconfigure the context with the written results
        configurator.configure(configIn);
        configOut.reset();
        configurator.writeConfiguration(configOut);
        configProps.clear();
        configProps.load(new InputStreamReader(new ByteArrayInputStream(configOut.toByteArray()), ENCODING));
        compare(dftProps, configProps);

    }

    @Test
    public void testPrepareAndRollback() throws Exception {
        final Properties defaultProperties = defaultProperties();
        final LogContext logContext = LogContext.create();
        final PropertyConfigurator configurator = new PropertyConfigurator(logContext);
        configurator.configure(defaultProperties);

        final LogContextConfiguration logContextConfiguration = configurator.getLogContextConfiguration();
        // Add a logger to be rolled back
        final LoggerConfiguration fooConfiguration = logContextConfiguration.addLoggerConfiguration("foo");

        // Add a handler to be rolled back
        final HandlerConfiguration handlerConfiguration = logContextConfiguration.addHandlerConfiguration(null, TestFileHandler.class.getName(), "removalFile");
        handlerConfiguration.setLevel("INFO");
        handlerConfiguration.setPostConfigurationMethods("flush");
        handlerConfiguration.setPropertyValueString("fileName", "removalFile.log");

        logContextConfiguration.prepare();
        logContextConfiguration.forget();

        // Make sure the logger and handler are not in the configuration
        assertNull("Logger not removed", logContextConfiguration.getLoggerConfiguration("foo"));
        assertNull("Handler not removed", logContextConfiguration.getLoggerConfiguration("removalFile"));

        // Reload output streams into properties
        final Properties configProps = new Properties();
        final ByteArrayOutputStream configOut = new ByteArrayOutputStream();
        configurator.writeConfiguration(configOut);
        final ByteArrayInputStream configIn = new ByteArrayInputStream(configOut.toByteArray());
        configProps.load(new InputStreamReader(configIn, ENCODING));
        compare(defaultProperties, configProps);

    }

    @Test
    public void testExpressions() throws Exception {
        final Properties defaultProperties = new Properties();
        defaultProperties.load(PropertyConfiguratorTests.class.getResourceAsStream("expression-logging.properties"));
        final LogContext logContext = LogContext.create();
        final PropertyConfigurator configurator = new PropertyConfigurator(logContext);
        configurator.configure(defaultProperties);

        // Write out the configuration
        final ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
        defaultProperties.store(new OutputStreamWriter(propsOut, ENCODING), null);
        final ByteArrayOutputStream configOut = new ByteArrayOutputStream();
        configurator.writeConfiguration(configOut, true);

        // Reload output streams into properties
        final Properties configProps = new Properties();
        final ByteArrayInputStream configIn = new ByteArrayInputStream(configOut.toByteArray());
        configProps.load(new InputStreamReader(configIn, ENCODING));
        final Properties dftProps = new Properties();
        dftProps.load(new InputStreamReader(new ByteArrayInputStream(propsOut.toByteArray()), ENCODING));
        compare(dftProps, configProps);

        // Reconfigure the context with the written results
        configurator.configure(configIn);
        configOut.reset();
        configurator.writeConfiguration(configOut, true);
        configProps.clear();
        configProps.load(new InputStreamReader(new ByteArrayInputStream(configOut.toByteArray()), ENCODING));
        compare(dftProps, configProps);

        // Test resolved values
        configOut.reset();
        configurator.writeConfiguration(configOut, false);
        configProps.clear();
        configProps.load(new InputStreamReader(new ByteArrayInputStream(configOut.toByteArray()), ENCODING));
        assertEquals("DEBUG", configProps.getProperty("logger.level"));
        assertEquals("SYSTEM_OUT", configProps.getProperty("handler.CONSOLE.target"));
        assertEquals("true", configProps.getProperty("handler.FILE.autoFlush").toLowerCase(Locale.ENGLISH));
        assertEquals(ENCODING, configProps.getProperty("handler.FILE.encoding"));
    }

    @Test
    public void testAddAndRemove() throws Exception {
        final StdErr stdErr = StdErr.create();
        try {
            final LogContext logContext = LogContext.create();

            final LogContextConfiguration logContextConfiguration = LogContextConfiguration.Factory.create(logContext);
            // Add a logger to add the pojo to
            LoggerConfiguration fooConfiguration = logContextConfiguration.addLoggerConfiguration("foo");

            HandlerConfiguration pojoHandler = logContextConfiguration.addHandlerConfiguration(null, PojoHandler.class.getName(), "pojo");
            PojoConfiguration pojoConfiguration = logContextConfiguration.addPojoConfiguration(null, PojoObject.class.getName(), "pojo");
            pojoHandler.setPropertyValueString("pojoObject", "pojo");

            pojoConfiguration.setPropertyValueString("name", "pojo");

            assertTrue(pojoConfiguration.addPostConfigurationMethod("init"));
            assertTrue(pojoConfiguration.addPostConfigurationMethod("checkInitialized"));

            assertTrue(fooConfiguration.addHandlerName("pojo"));

            logContextConfiguration.prepare();
            logContextConfiguration.commit();

            // Remove the configurations
            assertTrue(logContextConfiguration.removeHandlerConfiguration("pojo"));
            assertTrue(logContextConfiguration.removePojoConfiguration("pojo"));
            assertTrue(logContextConfiguration.removeLoggerConfiguration("foo"));
            // logContextConfiguration.prepare();


            // Re-add the configurations in the same transaction
            fooConfiguration = logContextConfiguration.addLoggerConfiguration("foo");

            pojoHandler = logContextConfiguration.addHandlerConfiguration(null, PojoHandler.class.getName(), "pojo");
            pojoConfiguration = logContextConfiguration.addPojoConfiguration(null, PojoObject.class.getName(), "pojo");
            pojoHandler.setPropertyValueString("pojoObject", "pojo");

            pojoConfiguration.setPropertyValueString("name", "pojo");

            assertTrue(pojoConfiguration.addPostConfigurationMethod("init"));
            assertTrue(pojoConfiguration.addPostConfigurationMethod("checkInitialized"));

            assertTrue(fooConfiguration.addHandlerName("pojo"));

            logContextConfiguration.prepare();
            logContextConfiguration.commit();

            // No errors should be output
            assertTrue("Error written to stderr", stdErr.getOutput().isEmpty());
        } finally {
            stdErr.close();
        }
    }

    @Test
    public void testReadInvalidConfig() throws Exception {
        final Properties defaultProperties = new Properties();
        defaultProperties.load(PropertyConfiguratorTests.class.getResourceAsStream("invalid-logging.properties"));
        final LogContext logContext = LogContext.create();
        final PropertyConfigurator configurator = new PropertyConfigurator(logContext);
        configurator.configure(defaultProperties);

        // Update the properties to how they should be written
        final Properties correctedDefaultProperties = new Properties();
        correctedDefaultProperties.putAll(defaultProperties);
        correctedDefaultProperties.setProperty("logger.handlers", "CONSOLE");
        correctedDefaultProperties.remove("handler.CONSOLE.errorManager");
        correctedDefaultProperties.remove("handler.FILE.formatter");

        // Write out the configuration
        final ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
        correctedDefaultProperties.store(new OutputStreamWriter(propsOut, ENCODING), null);
        final ByteArrayOutputStream configOut = new ByteArrayOutputStream();
        configurator.writeConfiguration(configOut);

        // Reload output streams into properties
        final Properties configProps = new Properties();
        final ByteArrayInputStream configIn = new ByteArrayInputStream(configOut.toByteArray());
        configProps.load(new InputStreamReader(configIn, ENCODING));
        final Properties dftProps = new Properties();
        dftProps.load(new InputStreamReader(new ByteArrayInputStream(propsOut.toByteArray()), ENCODING));
        compare(dftProps, configProps);

    }

    // TODO (jrp) Note that in the future this test could break as a handler really shouldn't be allowed to be removed if it's attached to a logger
    @Test
    public void testWriteInvalidConfig() throws Exception {
        final Properties defaultProperties = new Properties();
        defaultProperties.load(PropertyConfiguratorTests.class.getResourceAsStream("simple-logging.properties"));
        final LogContext logContext = LogContext.create();
        final PropertyConfigurator configurator = new PropertyConfigurator(logContext);
        configurator.configure(defaultProperties);

        /*defaultProperties.setProperty("handler.c2", ConsoleHandler.class.getName());
        defaultProperties.setProperty("loggers", defaultProperties.getProperty("loggers") + ",test.logger");
        defaultProperties.setProperty("logger.test.logger.handlers", "c2");*/

        // Write out the configuration
        final ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
        defaultProperties.store(new OutputStreamWriter(propsOut, ENCODING), null);

        // Update the configurator with invalid values
        final LogContextConfiguration configuration = configurator.getLogContextConfiguration();

        // Add a handler
        final HandlerConfiguration handlerConfiguration = configuration.addHandlerConfiguration(null, ConsoleHandler.class.getName(), "c2");

        final LoggerConfiguration loggerConfiguration = configuration.addLoggerConfiguration("test.logger");
        loggerConfiguration.addHandlerName("c2");

        configuration.commit();

        // Remove the handler in the same transaction
        configuration.removeHandlerConfiguration("c2");
        configuration.commit();


        final ByteArrayOutputStream configOut = new ByteArrayOutputStream();
        configurator.writeConfiguration(configOut);

        // Reload output streams into properties
        final Properties configProps = new Properties();
        final ByteArrayInputStream configIn = new ByteArrayInputStream(configOut.toByteArray());
        configProps.load(new InputStreamReader(configIn, ENCODING));
        final Properties dftProps = new Properties();
        dftProps.load(new InputStreamReader(new ByteArrayInputStream(propsOut.toByteArray()), ENCODING));
        compare(dftProps, configProps);

    }

    private void compare(final Properties defaultProps, final Properties configProps) {
        final Set<String> dftNames = defaultProps.stringPropertyNames();
        final Set<String> configNames = configProps.stringPropertyNames();
        // Look for missing keys
        final Set<String> missingDftNames = new TreeSet<String>(dftNames);
        missingDftNames.removeAll(configNames);
        final Set<String> missingConfigNames = new TreeSet<String>(configNames);
        missingConfigNames.removeAll(dftNames);
        assertTrue("Default properties are missing: " + missingDftNames, missingDftNames.isEmpty());
        assertTrue("Configuration properties are missing: " + missingConfigNames, missingConfigNames.isEmpty());

        // Values need to match
        for (String key : defaultProps.stringPropertyNames()) {
            final String dftValue = defaultProps.getProperty(key);
            final String configValue = configProps.getProperty(key);
            final String msg = String.format("Unmatched values: %nDefault: %20s%nConfiguration: %20s", dftValue, configValue);
            // Loggers require special handling as the order is not guaranteed
            if ("loggers".equals(key)) {
                final List<String> dftLoggers = Arrays.asList(dftValue.split("\\s*,\\s*"));
                final List<String> configLoggers = Arrays.asList(configValue.split("\\s*,\\s*"));
                assertTrue(msg, configLoggers.containsAll(dftLoggers));
            } else {
                assertEquals(msg, dftValue, configValue);
            }
        }
    }

    private static Properties defaultProperties() throws IOException {
        final Properties props = new Properties();
        props.load(PropertyConfiguratorTests.class.getResourceAsStream("default-logging.properties"));
        return props;
    }

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

    public static class PojoHandler extends ExtHandler {
        PojoObject pojoObject;

        @Override
        protected void doPublish(final ExtLogRecord record) {
            if (pojoObject != null) {
                final Formatter formatter = getFormatter();
                if (formatter != null) {
                    pojoObject.setValue(formatter.format(record));
                } else {
                    pojoObject.setValue(record.getFormattedMessage());
                }
                System.out.println(pojoObject.getValue());
            }
            super.doPublish(record);
        }

        public PojoObject getPojoObject() {
            return pojoObject;
        }

        public void setPojoObject(final PojoObject pojoObject) {
            this.pojoObject = pojoObject;
        }
    }

    static class StdErr extends PrintStream {
        private final PrintStream defaultErr;
        private final ByteArrayOutputStream out;

        static StdErr create() {
            final StdErr stdErr = new StdErr(new ByteArrayOutputStream());
            System.setErr(stdErr);
            return stdErr;
        }

        public StdErr(final ByteArrayOutputStream out) {
            super(out);
            this.out = out;
            defaultErr = System.err;
        }

        public synchronized String getOutput() {
            return out.toString();
        }

        @Override
        public synchronized void write(final int b) {
            super.write(b);
            defaultErr.write(b);
        }

        @Override
        public synchronized void write(final byte[] buf, final int off, final int len) {
            super.write(buf, off, len);
            defaultErr.write(buf, off, len);
        }

        @Override
        public synchronized void write(final byte[] b) throws IOException {
            super.write(b);
            defaultErr.write(b);
        }

        @Override
        public synchronized void flush() {
            super.flush();
            safeFlush(out);
            safeFlush(defaultErr);
        }

        @Override
        public synchronized void close() {
            flush();
            System.setErr(defaultErr);
            super.close();
        }

        static void safeFlush(final Flushable flushable) {
            if (flushable != null) try {
                flushable.flush();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }
}
