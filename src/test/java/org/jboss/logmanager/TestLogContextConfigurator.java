/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;

import org.jboss.logmanager.handlers.FileHandler;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TestLogContextConfigurator implements LogContextConfigurator {

    public TestLogContextConfigurator() {
        this(true);
    }

    TestLogContextConfigurator(final boolean assumedJul) {
        if (assumedJul) {
            configure(null);
        }
    }

    @Override
    public void configure(final LogContext logContext, final InputStream inputStream) {
        configure(logContext);
    }

    private static void configure(final LogContext logContext) {
        if (Boolean.getBoolean("org.jboss.logmanager.test.configure")) {
            final Logger rootLogger;
            if (logContext == null) {
                rootLogger = Logger.getLogger("");
            } else {
                rootLogger = logContext.getLogger("");
            }
            try {
                final String fileName = System.getProperty("test.log.file.name");
                final FileHandler handler = new FileHandler(fileName, false);
                handler.setAutoFlush(true);
                handler.setFormatter(new TestFormatter());
                rootLogger.addHandler(handler);
                rootLogger.setLevel(Level.INFO);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static class TestFormatter extends ExtFormatter {

        @Override
        public String format(final ExtLogRecord record) {
            StringWriter stringWriter = new StringWriter();
            try (JsonGenerator generator = Json.createGenerator(stringWriter)) {
                generator.writeStartObject();

                generator.write("loggerClassName", record.getLoggerClassName());
                generator.write("level", record.getLevel().toString());
                generator.write("message", record.getMessage());
                generator.writeStartObject("mdc");
                final Map<String, String> mdc = record.getMdcCopy();
                mdc.forEach(generator::write);
                generator.writeEnd(); // end MDC

                generator.writeEnd(); // end object
                generator.flush();
                stringWriter.write(System.lineSeparator());
                return stringWriter.toString();
            }
        }
    }
}
