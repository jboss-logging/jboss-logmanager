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

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.ErrorManager;
import java.util.stream.Collectors;

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
        handler = new PeriodicRotatingFileHandler(logFile.toFile(), rotateFormatter.toPattern(), false);
        handler.setFormatter(FORMATTER);
        // Set append to true to ensure the rotated file is overwritten
        handler.setAppend(true);
        handler.setErrorManager(AssertingErrorManager.of());
    }

    @After
    public void closeHandler() throws IOException {
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

    private List<Path> getBaseDirPaths() throws Exception {
        System.out.println("Paths:");
        List<Path> paths = Files.list(BASE_LOG_DIR.toPath())
            .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
            .collect(Collectors.toList());
        paths.forEach(System.out::println);
        return paths;
    }

    private List<File> getBaseDirFiles() throws Exception {
        System.out.println("Files:");
        List<File> files = Files.list(BASE_LOG_DIR.toPath())
            .map(Path::toFile)
            .sorted(Comparator.comparingLong(File::lastModified).reversed())
            .collect(Collectors.toList());
        files.forEach(System.out::println);
        return files;
    }

    @Test
    public void testPruneBasic() throws Exception {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());

        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd");

        createLogRecords(10, cal, currentDate, sdf);

        handler.setPruneSize("55");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        cal.add(Calendar.DAY_OF_MONTH, -1);

        List<Path> files = getBaseDirPaths();

        Assert.assertEquals(2, files.size());
        Assert.assertEquals("periodic-rotating-file-handler.log", files.get(0).getFileName().toString());
        Assert.assertEquals("periodic-rotating-file-handler.log." + sdf.format(cal.getTime()), files.get(1).getFileName().toString());

    }

    @Test
    public void testPrunePeriods() throws Exception {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());

        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd");

        createLogRecords(10, cal, currentDate, sdf);

        handler.setPruneSize("5p");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        cal.add(Calendar.DAY_OF_MONTH, -1);

        List<Path> files = getBaseDirPaths();
        Assert.assertEquals(6, files.size());
        Assert.assertEquals("periodic-rotating-file-handler.log", files.get(0).getFileName().toString());
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals("periodic-rotating-file-handler.log." + sdf.format(cal.getTime()), files.get(i + 1).getFileName().toString());
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }

    }

    @Test
    public void testPruneInvalidSize() throws Exception {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd");
        createLogRecords(10, cal, currentDate, sdf);

        handler.setPruneSize("-50");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);
    }
    @Test
    public void testPruneInvalidPeriods() throws Exception {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd");
        createLogRecords(10, cal, currentDate, sdf);

        handler.setPruneSize("-5p");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);
    }

    @Test
    public void testPruneIgnoreExtraFilesInDir() throws Exception {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());

        Files.createFile(Paths.get(BASE_LOG_DIR.toString(), "chaff.txt"));

        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd");

        createLogRecords(10, cal, currentDate, sdf);

        handler.setPruneSize("55");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        cal.add(Calendar.DAY_OF_MONTH, -1);

        List<Path> files = getBaseDirPaths();
        Assert.assertEquals(3, files.size());
        Assert.assertEquals("periodic-rotating-file-handler.log", files.get(0).getFileName().toString());
        Assert.assertEquals("periodic-rotating-file-handler.log." + sdf.format(cal.getTime()), files.get(1).getFileName().toString());
        Assert.assertEquals("chaff.txt", files.get(2).getFileName().toString());

    }

    @Test
    public void testPruneIgnoreFilesInNestedDir() throws Exception {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());

        Files.createDirectories(Paths.get(BASE_LOG_DIR.toString(), "nested"));
        Files.createFile(Paths.get(BASE_LOG_DIR.toString(), "nested", "periodic-rotating-file-handler.log.2019-10-15"));

        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd");

        createLogRecords(10, cal, currentDate, sdf);

        handler.setPruneSize("55");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        cal.add(Calendar.DAY_OF_MONTH, -1);

        List<File> files = getBaseDirFiles();
        Assert.assertEquals(3, files.size());
        Assert.assertEquals("periodic-rotating-file-handler.log", files.get(0).getName());
        Assert.assertEquals("periodic-rotating-file-handler.log." + sdf.format(cal.getTime()), files.get(1).getName());
        Assert.assertEquals("nested", files.get(2).getName());
    }

    @Test
    public void testPruneIgnoreSymlinks() throws Exception {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()));

        Path temp = Files.createTempFile("mytemp", ".tmp");
        Path linkPath = Files.createLink(Paths.get(BASE_LOG_DIR.toString(), "mytemp"), temp);
        Assert.assertTrue(linkPath.toFile().exists());

        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd");

        createLogRecords(10, cal, currentDate, sdf);

        handler.setPruneSize("55");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        cal.add(Calendar.DAY_OF_MONTH, -1);

        List<File> files = getBaseDirFiles();
        Assert.assertEquals(3, files.size());
        Assert.assertEquals("periodic-rotating-file-handler.log", files.get(0).getName());
        Assert.assertEquals("periodic-rotating-file-handler.log." + sdf.format(cal.getTime()), files.get(1).getName());
        Assert.assertEquals("mytemp", files.get(2).getName());

    }

    @Test
    public void testPruneIgnoreFilesWithInsufficientPerms() throws Exception {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()));

        Path roFile = Files.createFile(Paths.get(BASE_LOG_DIR.toString(), "periodic-rotating-file-handler.log.foo"));
        Assert.assertTrue(roFile.toFile().setReadable(true));
        Assert.assertTrue(roFile.toFile().setWritable(false));

        Assert.assertFalse(roFile.toFile().canWrite());

        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd");

        createLogRecords(10, cal, currentDate, sdf);

        handler.setPruneSize("55");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        cal.add(Calendar.DAY_OF_MONTH, -1);

        List<File> files = getBaseDirFiles();
        Assert.assertEquals(3, files.size());
        Assert.assertEquals("periodic-rotating-file-handler.log", files.get(0).getName());
        Assert.assertEquals("periodic-rotating-file-handler.log." + sdf.format(cal.getTime()), files.get(1).getName());
        Assert.assertEquals("periodic-rotating-file-handler.log.foo", files.get(2).getName());

        Assert.assertTrue(roFile.toFile().setWritable(true));
        Assert.assertTrue(roFile.toFile().setExecutable(true));

    }

    @Test
    public void testPruneArchive() throws Exception {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + rotateFormatter.format(cal.getTime()));

        String currentDate = sdf.format(cal.getTime());
        handler.setSuffix(".YYYY-MM-dd.zip");

        for (int i = 0; i < 10; i++) {

            // Create a log message to be logged
            ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
            record.setMillis(cal.getTimeInMillis());
            handler.publish(record);

            Assert.assertTrue("File '" + logFile + "' does not exist", Files.exists(logFile));

            // Read the contents of the log file and ensure there's only one line
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            Assert.assertEquals("More than 1 line found", 1, lines.size());
            Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));

            cal.add(Calendar.DAY_OF_MONTH, 1);
            cal.add(Calendar.MINUTE, 1);
            currentDate = sdf.format(cal.getTime());
        }

        handler.setPruneSize("400");
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        cal.add(Calendar.DAY_OF_MONTH, -1);

        List<Path> files = getBaseDirPaths();
        Assert.assertEquals(2, files.size());
        Assert.assertEquals("periodic-rotating-file-handler.log", files.get(0).getFileName().toString());
        Assert.assertEquals("periodic-rotating-file-handler.log." + sdf.format(cal.getTime()) + ".zip", files.get(1).getFileName().toString());

    }

    private void createLogRecords(int number, Calendar now, String currentDate, SimpleDateFormat formatter) throws Exception {
        for (int i = 0; i < number; i++) {

            // Create a log message to be logged
            ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
            record.setMillis(now.getTimeInMillis());
            handler.publish(record);

            // introduce a small pause to ensure that tests are determinate
            Thread.sleep(250L);

            Assert.assertTrue("File '" + logFile + "' does not exist", Files.exists(logFile));

            // Read the contents of the log file and ensure there's only one line
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            Assert.assertEquals("More than 1 line found", 1, lines.size());
            Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));

            now.add(Calendar.DAY_OF_MONTH, 1);
            now.add(Calendar.MINUTE, 1);
            currentDate = formatter.format(now.getTime());

        }
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

        // Read the contents of the log file and ensure there's only one line
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));

        // Create a new record, increment the day by one and validate
        cal.add(Calendar.DAY_OF_MONTH, nextDay);
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
    }
}
