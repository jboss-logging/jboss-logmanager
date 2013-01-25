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

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Test
public class PropertyConfiguratorTests {

    static {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        System.setProperty("default.log.level", "DEBUG");
    }

    public void testReadWrite() throws Exception {
        final Properties defaultProperties = defaultProperties();
        final LogContext logContext = LogContext.create();
        final PropertyConfigurator configurator = new PropertyConfigurator(logContext);
        configurator.configure(defaultProperties);

        // Write out the configuration
        final ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
        defaultProperties.store(new OutputStreamWriter(propsOut, "utf-8"), null);
        final ByteArrayOutputStream configOut = new ByteArrayOutputStream();
        configurator.writeConfiguration(configOut);

        // Reload output streams into properties
        final Properties configProps = new Properties();
        final ByteArrayInputStream configIn = new ByteArrayInputStream(configOut.toByteArray());
        configProps.load(new InputStreamReader(configIn, "utf-8"));
        final Properties dftProps = new Properties();
        dftProps.load(new InputStreamReader(new ByteArrayInputStream(propsOut.toByteArray()), "utf-8"));
        compare(dftProps, configProps);

        // Reconfigure the context with the written results
        configurator.configure(configIn);
        configOut.reset();
        configurator.writeConfiguration(configOut);
        configProps.clear();
        configProps.load(new InputStreamReader(new ByteArrayInputStream(configOut.toByteArray()), "utf-8"));
        compare(dftProps, configProps);

    }

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
        configProps.load(new InputStreamReader(configIn, "utf-8"));
        compare(defaultProperties, configProps);

    }

    public void testExpressions() throws Exception {
        final Properties defaultProperties = new Properties();
        defaultProperties.load(PropertyConfiguratorTests.class.getResourceAsStream("expression-logging.properties"));
        final LogContext logContext = LogContext.create();
        final PropertyConfigurator configurator = new PropertyConfigurator(logContext);
        configurator.configure(defaultProperties);

        // Write out the configuration
        final ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
        defaultProperties.store(new OutputStreamWriter(propsOut, "utf-8"), null);
        final ByteArrayOutputStream configOut = new ByteArrayOutputStream();
        configurator.writeConfiguration(configOut, true);

        // Reload output streams into properties
        final Properties configProps = new Properties();
        final ByteArrayInputStream configIn = new ByteArrayInputStream(configOut.toByteArray());
        configProps.load(new InputStreamReader(configIn, "utf-8"));
        final Properties dftProps = new Properties();
        dftProps.load(new InputStreamReader(new ByteArrayInputStream(propsOut.toByteArray()), "utf-8"));
        compare(dftProps, configProps);

        // Reconfigure the context with the written results
        configurator.configure(configIn);
        configOut.reset();
        configurator.writeConfiguration(configOut, true);
        configProps.clear();
        configProps.load(new InputStreamReader(new ByteArrayInputStream(configOut.toByteArray()), "utf-8"));
        compare(dftProps, configProps);

        // Test resolved values
        configOut.reset();
        configurator.writeConfiguration(configOut, false);
        configProps.clear();
        configProps.load(new InputStreamReader(new ByteArrayInputStream(configOut.toByteArray()), "utf-8"));
        assertEquals("DEBUG", configProps.getProperty("logger.level"));
        assertEquals("SYSTEM_OUT", configProps.getProperty("handler.CONSOLE.target"));
        assertEquals("true", configProps.getProperty("handler.FILE.autoFlush").toLowerCase(Locale.ENGLISH));
        assertEquals("UTF-8", configProps.getProperty("handler.FILE.encoding"));
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
}
