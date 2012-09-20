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
import static org.testng.AssertJUnit.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.logmanager.handlers.ConsoleHandler.Target;
import org.jboss.logmanager.handlers.FileHandler;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Test
public class PropertyConfiguratorTests {
    static {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
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

    private void compare(final Properties defaultProps, final Properties configProps) {
        // Compare keys and values
        assertTrue(String.format("Properties are a different size: %nDefault: %20s%nConfiguration: %20s%n", defaultProps, configProps), configProps.size() == defaultProps.size());

        // Property names need to match
        final Set<String> dftNames = defaultProps.stringPropertyNames();
        final Set<String> configNames = configProps.stringPropertyNames();
        assertTrue(String.format("Unmatched keys: %nDefault: %20s%nConfiguration: %20s", dftNames, configNames), dftNames.containsAll(configNames));

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

    private static Properties defaultProperties() {
        final Properties props = new Properties();
        final String loggerName = PropertyConfiguratorTests.class.getPackage().getName();
        final String spacedLoggerName = "Spaced Logger";
        final String specialCharLoggerName = "Special:Char\\Logger";
        // Create the loggers
        props.setProperty("loggers", loggerName.concat(",").concat(spacedLoggerName).concat(",").concat(specialCharLoggerName));
        props.setProperty("logger.level", "INFO");
        props.setProperty("logger.handlers", "CONSOLE");
        loggerInit(props, loggerName);
        loggerInit(props, spacedLoggerName);
        loggerInit(props, specialCharLoggerName);

        // An explicit console handler
        props.setProperty("handler.CONSOLE", ConsoleHandler.class.getName());
        props.setProperty("handler.CONSOLE.formatter", "PATTERN");
        props.setProperty("handler.CONSOLE.properties", "autoFlush,target");
        props.setProperty("handler.CONSOLE.autoFlush", Boolean.toString(true));
        props.setProperty("handler.CONSOLE.target", Target.SYSTEM_OUT.toString());

        // An implicit handler
        props.setProperty("handler.FILE", FileHandler.class.getName());
        props.setProperty("handler.FILE.formatter", "PATTERN");
        props.setProperty("handler.FILE.level", "TRACE");
        props.setProperty("handler.FILE.properties", "autoFlush,append,fileName");
        props.setProperty("handler.FILE.constructorProperties", "fileName,append");
        props.setProperty("handler.FILE.autoFlush", Boolean.toString(true));
        props.setProperty("handler.FILE.append", Boolean.toString(false));
        props.setProperty("handler.FILE.fileName", "logs/test.log");
        props.setProperty("handlers", "FILE");

        // An explicit filter - currently these are not supported
        //        props.setProperty("filter.FILTER", RegexFilter.class.getName());
        //        props.setProperty("filter.FILTER.properties", "patternString");
        //        props.setProperty("filter.FILTER.constructorProperties", "patternString");
        //        props.setProperty("filter.FILTER.patternString", ".*");
        //        props.setProperty("handler.FILE.filter", "FILTER");

        // An implicit filter
        props.setProperty("filters", "FILTER2");
        props.setProperty("filter.FILTER2", AcceptFilter.class.getName());

        // An implicit error manager
        props.setProperty("errorManager.DFT", ErrorManager.class.getName());
        props.setProperty("handler.FILE.errorManager", "DFT");

        // An explicit error manager
        props.setProperty("errorManagers", "OTHER");
        props.setProperty("errorManager.OTHER", ErrorManager.class.getName());

        // An implicit pattern
        props.setProperty("formatter.PATTERN", "org.jboss.logmanager.formatters.PatternFormatter");
        props.setProperty("formatter.PATTERN.properties", "pattern");
        props.setProperty("formatter.PATTERN.pattern", "%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");

        // An explicit pattern
        props.setProperty("formatters", "OTHER");
        props.setProperty("formatter.OTHER", "org.jboss.logmanager.formatters.PatternFormatter");
        props.setProperty("formatter.OTHER.properties", "pattern");
        props.setProperty("formatter.OTHER.pattern", "%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");
        return props;
    }

    private static void loggerInit(final Properties props, final String loggerName) {
        props.setProperty(String.format("logger.%s.useParentHandlers", loggerName), Boolean.toString(true));
        props.setProperty(String.format("logger.%s.level", loggerName), "INFO");
    }

    public static class AcceptFilter implements Filter {

        @Override
        public boolean isLoggable(final LogRecord record) {
            return true;
        }
    }
}
