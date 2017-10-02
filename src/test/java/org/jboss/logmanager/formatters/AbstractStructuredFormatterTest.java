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

package org.jboss.logmanager.formatters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Formatter;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractStructuredFormatterTest extends AbstractTest {
    private static final Charset ENCODING = StandardCharsets.UTF_8;
    private static final String MSG = "This is a test message";

    @Test
    public void testReadWrite() throws Exception {
        final Properties defaultProperties = getProperties();
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
        compareProperties(dftProps, configProps);

        // Reconfigure the context with the written results
        configurator.configure(configIn);
        configOut.reset();
        configurator.writeConfiguration(configOut, true);
        configProps.clear();
        configProps.load(new InputStreamReader(new ByteArrayInputStream(configOut.toByteArray()), ENCODING));
        compareProperties(dftProps, configProps);

    }

    @Test
    public void testMetaDataReadWrite() throws Exception {
        final Properties properties = getProperties();
        LogContext logContext = LogContext.create();
        PropertyConfigurator configurator = new PropertyConfigurator(logContext);
        configurator.configure(properties);

        logContext.getLogger("org.jboss.logmanager.formatters.test").info(MSG);
        final String formattedMessage = StringQueueHandler.QUEUE.poll(3L, TimeUnit.SECONDS);
        Assert.assertNotNull("Message was not written within 3 seconds", formattedMessage);
        compare(createExpectedValues(), formattedMessage);

        // Write the configuration, reinitialize and ensure the metaData was correctly written and parsed
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        configurator.writeConfiguration(out, true);
        logContext = LogContext.create();
        configurator = new PropertyConfigurator(logContext);
        configurator.configure(new ByteArrayInputStream(out.toByteArray()));

        logContext.getLogger("org.jboss.logmanager.formatters.test").info(MSG);
        compare(createExpectedValues(), StringQueueHandler.QUEUE.poll(3L, TimeUnit.SECONDS));
    }

    Map<String, Consumer<String>> createExpectedValues() {
        final Map<String, Consumer<String>> expectedValues = new LinkedHashMap<>();
        // The "timestamp" key should be replaced with dateTime. We don't care what the value is only that it exists
        expectedValues.put("dateTime", Assert::assertNotNull);
        expectedValues.put("sequence", Assert::assertNotNull);
        expectedValues.put("loggerClassName", value -> Assert.assertEquals("org.jboss.logmanager.Logger", value));
        expectedValues.put("loggerName", value -> Assert.assertEquals("org.jboss.logmanager.formatters.test", value));
        expectedValues.put("level", value -> Assert.assertEquals("INFO", value));
        expectedValues.put("msg", value -> Assert.assertEquals(MSG, value));
        expectedValues.put("key1=", value -> Assert.assertEquals("value1", value));
        expectedValues.put("key2\\", value -> Assert.assertEquals("value2=", value));
        expectedValues.put("key3", value -> Assert.assertEquals("defaultValue3", value));
        expectedValues.put("key4", value -> Assert.assertEquals("value\\4", value));
        expectedValues.put("key5", Assert::assertNull);
        expectedValues.put("sourceClassName", value -> Assert.assertEquals("org.jboss.logmanager.formatters.AbstractStructuredFormatterTest", value));
        expectedValues.put("sourceFileName", value -> Assert.assertEquals("AbstractStructuredFormatterTest.java", value));
        return expectedValues;
    }

    abstract Class<? extends StructuredFormatter> getFormatterType();

    abstract void compare(final Map<String, Consumer<String>> expectedValues, final String formattedMessage) throws Exception;

    private Properties getProperties() {
        final String formatterName = "test-formatter";
        final String handlerName = "QUEUE";
        final Properties configProperties = new Properties();

        // Configure the loggers
        configProperties.setProperty("loggers", "org.jboss.logmanager.formatters");
        configProperties.setProperty("logger.org.jboss.logmanager.formatters.handlers", "QUEUE");

        // Configure the handler
        configProperties.setProperty("handler." + handlerName, StringQueueHandler.class.getName());
        configProperties.setProperty("handler." + handlerName + ".formatter", formatterName);

        // Configure the formatter
        configProperties.setProperty("formatter." + formatterName, getFormatterType().getName());
        configProperties.setProperty("formatter." + formatterName + ".constructorProperties", "keyOverrides");
        configProperties.setProperty("formatter." + formatterName + ".properties", "prettyPrint,printDetails,metaData,exceptionOutputType,dateFormat,zoneId,keyOverrides");
        configProperties.setProperty("formatter." + formatterName + ".prettyPrint", "true");
        configProperties.setProperty("formatter." + formatterName + ".printDetails", "true");
        configProperties.setProperty("formatter." + formatterName + ".metaData", "key1\\==value1,key2\\\\=value2=,key3=${test.expression.value3:defaultValue3},key4=value\\\\4,key5=");
        configProperties.setProperty("formatter." + formatterName + ".exceptionOutputType", "DETAILED_AND_FORMATTED");
        configProperties.setProperty("formatter." + formatterName + ".dateFormat", "yyyy-MM-dd'T'HH:mm:ssSSS");
        configProperties.setProperty("formatter." + formatterName + ".zoneId", "GMT");
        configProperties.setProperty("formatter." + formatterName + ".keyOverrides", "MESSAGE=msg,TIMESTAMP=dateTime,exception-caused-by=cause");
        return configProperties;
    }

    private void compareProperties(final Properties defaultProps, final Properties configProps) {
        final Set<String> dftNames = defaultProps.stringPropertyNames();
        final Set<String> configNames = configProps.stringPropertyNames();
        // Look for missing keys
        final Set<String> missingDftNames = new TreeSet<>(dftNames);
        missingDftNames.removeAll(configNames);
        final Set<String> missingConfigNames = new TreeSet<>(configNames);
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

    public static class StringQueueHandler extends ExtHandler {
        static final BlockingQueue<String> QUEUE = new LinkedBlockingDeque<>();

        @Override
        protected void doPublish(final ExtLogRecord record) {
            final Formatter formatter = getFormatter();
            assert formatter != null;
            QUEUE.offer(formatter.format(record));
        }

        @Override
        public void close() throws SecurityException {
            QUEUE.clear();
        }
    }
}
