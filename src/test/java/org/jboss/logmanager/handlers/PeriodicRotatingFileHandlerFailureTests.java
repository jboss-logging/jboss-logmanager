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

package org.jboss.logmanager.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.logging.ErrorManager;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@WithByteman
public class PeriodicRotatingFileHandlerFailureTests extends AbstractHandlerTest {
    private final static String FILENAME = "periodic-rotating-file-handler.log";

    private Path logFile;

    private final SimpleDateFormat rotateFormatter = new SimpleDateFormat(".dd");
    private PeriodicRotatingFileHandler handler;

    @BeforeEach
    public void createHandler() throws IOException {
        if (logFile == null) {
            logFile = resolvePath(FILENAME);
        }
        // Create the handler
        handler = new PeriodicRotatingFileHandler(logFile.toFile(), rotateFormatter.toPattern(), false);
        handler.setFormatter(FORMATTER);
        // Set append to true to ensure the rotated file is overwritten
        handler.setAppend(true);
        handler.setErrorManager(AssertingErrorManager.of());
    }

    @AfterEach
    public void closeHandler() {
        handler.close();
        handler = null;
    }

    @Test
    @BMRule(name = "Test failed rotated", targetClass = "java.nio.file.Files", targetMethod = "move", targetLocation = "AT ENTRY", condition = "$2.getFileName().toString().matches(\"periodic-rotating-file-handler\\\\.log\\\\.\\\\d+\")", action = "throw new IOException(\"Fail on purpose\")")
    public void testFailedRotate() throws Exception {
        final Calendar cal = Calendar.getInstance();
        final Path rotatedFile = resolvePath(FILENAME + rotateFormatter.format(cal.getTime()));
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        final int currentDay = cal.get(Calendar.DAY_OF_MONTH);
        final int nextDay = currentDay + 1;
        // Set to false for this specific test
        handler.setAppend(false);
        handler.setErrorManager(AssertingErrorManager.of(ErrorManager.GENERIC_FAILURE));

        final String currentDate = sdf.format(cal.getTime());

        // Create a log message to be logged
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        handler.publish(record);

        Assertions.assertTrue(Files.exists(logFile), () -> "File '" + logFile + "' does not exist");

        // Read the contents of the log file and ensure there's only one line
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assertions.assertEquals(1, lines.size(), "More than 1 line found");
        Assertions.assertTrue(lines.get(0).contains(currentDate), "Expected the line to contain the date: " + currentDate);

        // Create a new record, increment the day by one. The file should fail rotating, but the contents of the
        // log file should contain the new data
        cal.add(Calendar.DAY_OF_MONTH, nextDay);
        final String nextDate = sdf.format(cal.getTime());
        record = createLogRecord(Level.INFO, "Date: %s", nextDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        // Read the contents of the log file and ensure there's only one line
        lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assertions.assertEquals(1, lines.size(), "More than 1 line found");
        Assertions.assertTrue(lines.get(0).contains(nextDate), "Expected the line to contain the date: " + nextDate);

        // The file should not have been rotated
        Assertions.assertTrue(Files.notExists(rotatedFile),
                () -> "The rotated file '" + rotatedFile + "' exists and should not");
    }
}
