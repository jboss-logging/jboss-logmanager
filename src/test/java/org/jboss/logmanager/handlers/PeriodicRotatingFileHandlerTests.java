/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PeriodicRotatingFileHandlerTests extends AbstractHandlerTest {
    private final static String FILENAME = "periodic-rotating-file-handler.log";

    private final Path logFile = BASE_LOG_DIR.toPath().resolve(FILENAME);

    private final SimpleDateFormat rotateFormatter = new SimpleDateFormat(".dd");
    private PeriodicRotatingFileHandler handler;

    @Before
    public void createHandler() throws FileNotFoundException {
        // Create the handler
        handler = new PeriodicRotatingFileHandler(logFile.toFile(), rotateFormatter.toPattern(), false);
        handler.setFormatter(FORMATTER);
    }

    @After
    public void closeHandler() {
        handler.close();
        handler = null;
    }

    @Test
    public void testRotate() throws Exception {
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(createCalendar().getTime()));
        testRotate(createCalendar(), rotatedFile);
    }

    @Test
    public void testOverwriteRotate() throws Exception {
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(createCalendar().getTime()));

        // Create the rotated file to ensure at some point it gets overwritten
        Files.deleteIfExists(rotatedFile);
        try (final BufferedWriter writer = Files.newBufferedWriter(rotatedFile, StandardCharsets.UTF_8)) {
            writer.write("Adding data to the file");
        }
        testRotate(createCalendar(), rotatedFile);
    }


    private void testRotate(final Calendar cal, final Path rotatedFile) throws Exception {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        final int currentDay = cal.get(Calendar.DAY_OF_MONTH);
        final int nextDay = currentDay + 1;

        final String currentDate = sdf.format(cal.getTime());

        // Create a log message to be logged
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        handler.publish(record);

        Assert.assertTrue("File '" + logFile + "' does not exist", Files.exists(logFile));

        // Read the contents of the log file and ensure there's only one line and that like
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));

        // Create a new record, increment the day by one and validate
        cal.set(Calendar.DAY_OF_MONTH, nextDay);
        final String nextDate = sdf.format(cal.getTime());
        record = createLogRecord(Level.INFO, "Date: %s", nextDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        // Read the contents of the log file and ensure there's only one line and that like
        lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + nextDate, lines.get(0).contains(nextDate));

        // The file should have been rotated as well
        Assert.assertTrue("The rotated file '" + rotatedFile.toString() + "' does not exist", Files.exists(rotatedFile));
        lines = Files.readAllLines(rotatedFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));
    }

    private Calendar createCalendar() {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, 2015);
        calendar.set(Calendar.MONTH, Calendar.OCTOBER);
        calendar.set(Calendar.DAY_OF_MONTH, 21);
        return calendar;
    }
}
