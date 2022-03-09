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

package org.jboss.logmanager.ext.handlers;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.logging.ErrorManager;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(BMUnitRunner.class)
public class PeriodicRotatingFileHandlerTests extends AbstractHandlerTest {
    private final static String FILENAME = "periodic-rotating-file-handler.log";

    private final Path logFile = BASE_LOG_DIR.toPath().resolve(FILENAME);

    private final SimpleDateFormat rotateFormatter = new SimpleDateFormat(".dd");
    private PeriodicRotatingFileHandler handler;

    @Before
    public void createHandler() throws FileNotFoundException {
        // Create the handler
        handler = new PeriodicRotatingFileHandler(logFile.toFile(), rotateFormatter.toPattern(), 3, false);
        handler.setFormatter(FORMATTER);
        // Set append to true to ensure the rotated file is overwritten
        handler.setAppend(true);
        handler.setErrorManager(AssertingErrorManager.of());
    }

    @After
    public void closeHandler() {
        handler.close();
        handler = null;
    }

    @Test
    public void testRotate() throws Exception {
        final Calendar cal = Calendar.getInstance();
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()));
        testRotate(cal, rotatedFile);
    }

    @Test
    public void testOverwriteRotate() throws Exception {
        final Calendar cal = Calendar.getInstance();
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()));

        // Create the rotated file to ensure at some point it gets overwritten
        Files.deleteIfExists(rotatedFile);
        try (final BufferedWriter writer = Files.newBufferedWriter(rotatedFile, StandardCharsets.UTF_8)) {
            writer.write("Adding data to the file");
        }
        testRotate(cal, rotatedFile);
    }

    @Test
    public void testArchiveRotateGzip() throws Exception {
        testArchiveRotate(".gz");
    }

    @Test
    public void testArchiveRotateZip() throws Exception {
        testArchiveRotate(".zip");
    }

    @Test
    @BMRule(name = "Test failed rotated",
            targetClass = "java.nio.file.Files",
            targetMethod = "move",
            targetLocation = "AT ENTRY",
            condition = "$2.getFileName().toString().matches(\"periodic-rotating-file-handler\\\\.log\\\\.\\\\d+\")",
            action = "throw new IOException(\"Fail on purpose\")")
    public void testFailedRotate() throws Exception {
        final Calendar cal = Calendar.getInstance();
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()));
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

        Assert.assertTrue("File '" + logFile + "' does not exist", Files.exists(logFile));

        // Read the contents of the log file and ensure there's only one line
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));

        // Create a new record, increment the day by one. The file should fail rotating, but the contents of the
        // log file should contain the new data
        cal.add(Calendar.DAY_OF_MONTH, nextDay);
        final String nextDate = sdf.format(cal.getTime());
        record = createLogRecord(Level.INFO, "Date: %s", nextDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        // Read the contents of the log file and ensure there's only one line
        lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + nextDate, lines.get(0).contains(nextDate));

        // The file should not have been rotated
        Assert.assertTrue("The rotated file '" + rotatedFile.toString() + "' exists and should not", Files.notExists(rotatedFile));
    }


    private void testRotate(final Calendar cal, final Path rotatedFile) throws Exception {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        final String currentDate = sdf.format(cal.getTime());

        // Create a log message to be logged
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        handler.publish(record);

        Assert.assertTrue("File '" + logFile + "' does not exist", Files.exists(logFile));

        // Read the contents of the log file and ensure there's only one line
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));

        // Create a new record, increment the day by one and validate
        cal.add(Calendar.DAY_OF_MONTH, 1);
        final String nextDate = sdf.format(cal.getTime());
        record = createLogRecord(Level.INFO, "Date: %s", nextDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        // Read the contents of the log file and ensure there's only one line
        lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + nextDate, lines.get(0).contains(nextDate));

        // The file should have been rotated as well
        Assert.assertTrue("The rotated file '" + rotatedFile.toString() + "' does not exist", Files.exists(rotatedFile));
        lines = Files.readAllLines(rotatedFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));

        cal.add(Calendar.DAY_OF_MONTH, 1);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        cal.add(Calendar.DAY_OF_MONTH, -3);
        Assert.assertFalse(Files.exists(BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()))));
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Assert.assertTrue(Files.exists(BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()))));
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Assert.assertTrue(Files.exists(BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()))));
    }

    private void testArchiveRotate(final String archiveSuffix) throws Exception {
        final String rotationFormat = ".dd";
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final DateTimeFormatter rotateFormatter = DateTimeFormatter.ofPattern(rotationFormat);
        ZonedDateTime date = ZonedDateTime.now();

        handler.setSuffix(rotationFormat + archiveSuffix);

        final String currentDate = formatter.format(date);
        final String firstDateSuffix = rotateFormatter.format(date);

        // Create a log message to be logged
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        handler.publish(record);

        // Create a new record, increment the day by one and validate
        date = date.plusDays(1);
        final String secondDateSuffix = rotateFormatter.format(date);
        final String nextDate = formatter.format(date);
        record = createLogRecord(Level.INFO, "Date: %s", nextDate);
        record.setMillis(date.toInstant().toEpochMilli());
        handler.publish(record);

        // Create a new record, increment the day by one and validate
        date = date.plusDays(1);
        final String thirdDateSuffix = rotateFormatter.format(date);
        final String thirdDay = formatter.format(date);
        record = createLogRecord(Level.INFO, "Date: %s", thirdDay);
        record.setMillis(date.toInstant().toEpochMilli());
        handler.publish(record);

        // There should be three files
        final Path logDir = BASE_LOG_DIR.toPath();
        final Path rotated1 = logDir.resolve(FILENAME + firstDateSuffix + archiveSuffix);
        final Path rotated2 = logDir.resolve(FILENAME + secondDateSuffix + archiveSuffix);
        Assert.assertTrue("Missing file " + logFile, Files.exists(logFile));
        Assert.assertTrue("Missing rotated file " + rotated1, Files.exists(rotated1));
        Assert.assertTrue("Missing rotated file " + rotated2, Files.exists(rotated2));

        // Validate the files are not empty and the compressed file contains at least one log record
        if (archiveSuffix.endsWith(".gz")) {
            validateGzipContents(rotated1, "Date: " + currentDate);
            validateGzipContents(rotated2, "Date: " + nextDate);
        } else if (archiveSuffix.endsWith(".zip")) {
            validateZipContents(rotated1, logFile.getFileName().toString(), "Date: " + currentDate);
            validateZipContents(rotated2, logFile.getFileName().toString(), "Date: " + nextDate);
        } else {
            Assert.fail("Unknown archive suffix: " + archiveSuffix);
        }
        compareArchiveContents(rotated1, rotated2, logFile.getFileName().toString());

        date = date.plusDays(1);
        final String fourthDay = formatter.format(date);
        record = createLogRecord(Level.INFO, "Date: %s", fourthDay);
        record.setMillis(date.toInstant().toEpochMilli());
        handler.publish(record);

        // There are three files, not four files.
        final Path rotated3 = logDir.resolve(FILENAME + thirdDateSuffix + archiveSuffix);
        Assert.assertTrue(Files.exists(logFile));
        Assert.assertFalse(Files.exists(rotated1));
        Assert.assertTrue(Files.exists(rotated2));
        Assert.assertTrue(Files.exists(rotated3));
    }
}
