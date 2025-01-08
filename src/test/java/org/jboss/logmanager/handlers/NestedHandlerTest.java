/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class NestedHandlerTest {

    public static class TestHandler extends Handler {
        final BlockingDeque<ExtLogRecord> messages = new LinkedBlockingDeque<>();
        final ExtFormatter formatter;

        TestHandler() {
            this.formatter = new PatternFormatter("%s");
        }

        @Override
        public void publish(final LogRecord record) {
            final ExtLogRecord extLogRecord;
            if (record instanceof ExtLogRecord) {
                extLogRecord = (ExtLogRecord) record;
            } else {
                extLogRecord = new ExtLogRecord(Level.ERROR, String.format("An ExtLogRecord was expected but was %s", record),
                        ExtLogRecord.FormatStyle.PRINTF, NestedHandlerTest.class.getPackage().getName());
            }
            messages.add(extLogRecord);
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {
            messages.clear();
        }
    }

    @Test
    public void julHandler() {
        final TestHandler handler = new TestHandler();
        handler.setErrorManager(AssertingErrorManager.of());
        try (DelayedHandler delayedHandler = new DelayedHandler()) {
            delayedHandler.addHandler(handler);
            final ExtLogRecord record = new ExtLogRecord(Level.INFO, "FormatStyle %s", ExtLogRecord.FormatStyle.PRINTF,
                    NestedHandlerTest.class.getPackage().getName());
            record.setParameters(new Object[] { "PRINTF" });
            delayedHandler.publish(record);
            final var found = handler.messages.poll();
            Assertions.assertNotNull(found);
            Assertions.assertEquals("FormatStyle PRINTF", found.getMessage());
            Assertions.assertEquals(ExtLogRecord.FormatStyle.NO_FORMAT, found.getFormatStyle());
        }
    }

    @Test
    public void loggerNode() throws Exception {
        final TestHandler handler = new TestHandler();
        handler.setErrorManager(AssertingErrorManager.of());
        try (LogContext context = LogContext.getLogContext()) {
            final Logger logger = context.getLogger(NestedHandlerTest.class.getName());
            logger.addHandler(handler);
            final ExtLogRecord record = new ExtLogRecord(Level.INFO, "FormatStyle %s", ExtLogRecord.FormatStyle.PRINTF,
                    NestedHandlerTest.class.getPackage().getName());
            record.setParameters(new Object[] { "PRINTF" });
            logger.log(record);
            final var found = handler.messages.poll();
            Assertions.assertNotNull(found);
            Assertions.assertEquals("FormatStyle PRINTF", found.getMessage());
            Assertions.assertEquals(ExtLogRecord.FormatStyle.NO_FORMAT, found.getFormatStyle());
        }
    }
}
