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

package org.jboss.logmanager.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogContextConfigurationTests {
    private static final String LOGGER_NAME = LogContextConfigurationTests.class.getName();
    private static final Collection<FilterDescription> FILTER_DESCRIPTIONS = new ArrayList<>();

    @BeforeClass
    public static void setup() {
        FILTER_DESCRIPTIONS.add(new FilterDescription("substituteAll(\"\\\\s\", \"_\")", " test ", "_test_", true));
        FILTER_DESCRIPTIONS.add(new FilterDescription("substituteAll(\"\\\"\", \"'\")", "Test \"quoted\" replacement", "Test 'quoted' replacement", true));
        FILTER_DESCRIPTIONS.add(new FilterDescription("substitute(\"\\\\s\", \"_\")", " test ", "_test ", true));
        FILTER_DESCRIPTIONS.add(new FilterDescription("substitute(\"\\\\n\", \"\\r\\n\")", "Test Unix to Windows\n", "Test Unix to Windows\r\n", true));
        FILTER_DESCRIPTIONS.add(new FilterDescription("accept", "test accept", true));
        FILTER_DESCRIPTIONS.add(new FilterDescription("deny", "test deny", false));
        FILTER_DESCRIPTIONS.add(new FilterDescription("match(\"\\\\f\")", "test match \u000C", true));
        FILTER_DESCRIPTIONS.add(new FilterDescription("match(\"[a-zA-Z \\\"]+\")", "Test \"quoted\" text", true));
        FILTER_DESCRIPTIONS.add(new FilterDescription("match(\"\\\\s+\")", "test_match_no_spaces", false));
        FILTER_DESCRIPTIONS.add(new FilterDescription("not(match(\"\\\\b\"))", "Test word boundaries", false));
    }

    @Test
    public void testFilterParsing() throws Exception {
        final LogContext context = LogContext.create();
        final LogContextConfiguration configuration = LogContextConfiguration.Factory.create(context);
        final LoggerConfiguration loggerConfiguration = configuration.addLoggerConfiguration(LOGGER_NAME);
        final Logger logger = context.getLogger(LOGGER_NAME);

        for (FilterDescription description : FILTER_DESCRIPTIONS) {
            // Set the filter and commit
            loggerConfiguration.setFilter(description.filterExpression);
            configuration.commit();

            final ExtLogRecord record = create(description.logMessage);

            final Filter filter = logger.getFilter();
            Assert.assertNotNull("Expected a filter on the logger, but one was not found: " + description, filter);
            Assert.assertEquals("Filter.isLoggable() test failed: " + description, description.isLoggable, filter.isLoggable(record));
            final String msg = record.getFormattedMessage();
            Assert.assertEquals(String.format("Expected %s found %s: %n%s", description.expectedMessage, msg, description), description.expectedMessage, msg);
        }
    }

    @Test
    public void testNamedFilter() {
        final LogContext context = LogContext.create();
        final LogContextConfiguration configuration = LogContextConfiguration.Factory.create(context);
        final LoggerConfiguration loggerConfiguration = configuration.addLoggerConfiguration(LOGGER_NAME);
        final Logger logger = context.getLogger(LOGGER_NAME);

        final FilterDescription description = new FilterDescription("test", "test named filter",
                "test named filter | filtered", true);

        configuration.addFilterConfiguration(null, TestFilter.class.getName(), "test");
        loggerConfiguration.setFilter(description.filterExpression);
        configuration.commit();

        final ExtLogRecord record = create(description.logMessage);

        final Filter filter = logger.getFilter();
        Assert.assertNotNull("Expected a filter on the logger, but one was not found: " + description, filter);
        Assert.assertEquals("Filter.isLoggable() test failed: " + description, description.isLoggable, filter.isLoggable(record));
        final String msg = record.getFormattedMessage();
        Assert.assertEquals(String.format("Expected %s found %s: %n%s", description.expectedMessage, msg, description), description.expectedMessage, msg);
    }

    @Test
    public void testEmbeddedNamedFilter() {
        final LogContext context = LogContext.create();
        final LogContextConfiguration configuration = LogContextConfiguration.Factory.create(context);
        final LoggerConfiguration loggerConfiguration = configuration.addLoggerConfiguration(LOGGER_NAME);
        final Logger logger = context.getLogger(LOGGER_NAME);

        final FilterDescription description = new FilterDescription("all(test)", "test named filter",
                "test named filter | filtered", true);
        configuration.addFilterConfiguration(null, TestFilter.class.getName(), "test");
        loggerConfiguration.setFilter(description.filterExpression);
        configuration.commit();

        final ExtLogRecord record = create(description.logMessage);

        final Filter filter = logger.getFilter();
        Assert.assertNotNull("Expected a filter on the logger, but one was not found: " + description, filter);
        Assert.assertEquals("Filter.isLoggable() test failed: " + description, description.isLoggable, filter.isLoggable(record));
        final String msg = record.getFormattedMessage();
        Assert.assertEquals(String.format("Expected %s found %s: %n%s", description.expectedMessage, msg, description), description.expectedMessage, msg);
    }

    private static ExtLogRecord create(final String message) {
        final ExtLogRecord record = new ExtLogRecord(Level.INFO, message, Logger.class.getName());
        record.setLoggerName(LOGGER_NAME);
        return record;
    }

    private static class FilterDescription {
        final String filterExpression;
        final String logMessage;
        final String expectedMessage;
        final boolean isLoggable;

        private FilterDescription(final String filterExpression, final String message, final boolean isLoggable) {
            this(filterExpression, message, message, isLoggable);
        }

        private FilterDescription(final String filterExpression, final String logMessage, final String expectedMessage, final boolean isLoggable) {
            this.filterExpression = filterExpression;
            this.logMessage = logMessage;
            this.expectedMessage = expectedMessage;
            this.isLoggable = isLoggable;
        }

        @Override
        public String toString() {
            return "FilterDescription(filterExpression=" + filterExpression +
                    ", logMessage=" + logMessage +
                    ", expectedMessage=" + expectedMessage +
                    ", isLoggable=" + isLoggable + ")";
        }
    }

    public static class TestFilter implements Filter {

        @Override
        public boolean isLoggable(final LogRecord record) {
            record.setMessage(record.getMessage() + " | filtered");
            return true;
        }
    }
}
