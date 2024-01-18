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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ErrorManager;
import java.util.stream.Stream;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@WithByteman
public class PeriodicSizeRotatingFileHandlerTests extends AbstractHandlerTest {
    private final static String FILENAME = "rotating-file-handler.log";

    private Path logFile;

    private static final List<Integer> supportedPeriods = new ArrayList<Integer>();
    private static final Map<Integer, SimpleDateFormat> periodFormatMap = new HashMap<Integer, SimpleDateFormat>();

    static {
        supportedPeriods.add(Calendar.YEAR);
        supportedPeriods.add(Calendar.MONTH);
        supportedPeriods.add(Calendar.WEEK_OF_YEAR);
        supportedPeriods.add(Calendar.DAY_OF_MONTH);
        supportedPeriods.add(Calendar.AM_PM);
        supportedPeriods.add(Calendar.HOUR_OF_DAY);
        supportedPeriods.add(Calendar.MINUTE);

        //There are additional formats that could be tested here
        periodFormatMap.put(Calendar.YEAR, new SimpleDateFormat("yyyy"));
        periodFormatMap.put(Calendar.MONTH, new SimpleDateFormat("yyyy-MM"));
        periodFormatMap.put(Calendar.WEEK_OF_YEAR, new SimpleDateFormat("yyyy-ww"));
        periodFormatMap.put(Calendar.DAY_OF_MONTH, new SimpleDateFormat("yyyy-MM-dd"));
        periodFormatMap.put(Calendar.AM_PM, new SimpleDateFormat("yyyy-MM-dda"));
        periodFormatMap.put(Calendar.HOUR_OF_DAY, new SimpleDateFormat("yyyy-MM-dd-HH"));
        periodFormatMap.put(Calendar.MINUTE, new SimpleDateFormat("yyyy-MM-dd-HH-mm"));
    }

    @BeforeEach
    public void setup() throws IOException {
        if (logFile == null) {
            logFile = resolvePath(FILENAME);
        }
    }

    @Test
    public void testSizeRotate() throws Exception {
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        final Calendar cal = Calendar.getInstance();
        final String extension = "." + fmt.format(cal.getTimeInMillis());

        final PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(2);
        handler.setSuffix("." + fmt.toPattern());
        handler.setFile(logFile.toFile());

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // We should end up with 3 files, 2 rotated and the default log
        final Path file1 = resolvePath(FILENAME + extension + ".1");
        final Path file2 = resolvePath(FILENAME + extension + ".2");
        Assertions.assertTrue(Files.exists(logFile));
        Assertions.assertTrue(Files.exists(file1));
        Assertions.assertTrue(Files.exists(file2));
    }

    @Test
    public void testBootRotate() throws Exception {
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        final Calendar cal = Calendar.getInstance();
        final String extension = "." + fmt.format(cal.getTimeInMillis());

        PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        // Enough to not rotate
        handler.setRotateSize(5000L);
        handler.setMaxBackupIndex(1);
        handler.setSuffix("." + fmt.toPattern());
        handler.setRotateOnBoot(true);
        handler.setFile(logFile.toFile());
        final Path rotatedFile = resolvePath(FILENAME + extension + ".1");

        // The rotated file should not exist
        Assertions.assertFalse(Files.exists(rotatedFile), "Rotated file should not exist");

        // Log a few records
        for (int i = 0; i < 5; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        // Close the handler and create a new one
        handler.close();
        final long size = Files.size(logFile);
        handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(5000L);
        handler.setMaxBackupIndex(1);
        handler.setSuffix("." + fmt.toPattern());
        handler.setRotateOnBoot(true);
        handler.setFile(logFile.toFile());

        // The rotated file should exist
        Assertions.assertTrue(Files.exists(rotatedFile), "Rotated file should exist");

        // Rotated file size should match the size of the previous file
        Assertions.assertEquals(size, Files.size(rotatedFile));

        // Log a few records
        for (int i = 0; i < 10; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // File should have been rotated
        Assertions.assertTrue(Files.exists(logFile));
        Assertions.assertTrue(Files.exists(rotatedFile));

        // Neither file should be empty
        Assertions.assertTrue(Files.size(logFile) > 0L);
        Assertions.assertTrue(Files.size(rotatedFile) > 0L);
    }

    @Test
    @Disabled("LOGMGR-82")
    public void testPeriodicAndSizeRotate() throws Exception {
        for (int i = 0; i < supportedPeriods.size(); i++) {
            //To cut down on unnecessary testing, let's only test
            //the periods +/- two from this period
            int j = i - 2;
            if (j < 0)
                j = 0;

            int handlerPeriod = supportedPeriods.get(i);
            for (; j <= i + 2; j++) {
                if (j >= supportedPeriods.size())
                    break;
                int logMessagePeriod = supportedPeriods.get(j);
                testPeriodicAndSizeRotate0(handlerPeriod, logMessagePeriod, true);
                testPeriodicAndSizeRotate0(handlerPeriod, logMessagePeriod, false);
            }
        }
    }

    @Test
    public void testArchiveRotateGzip() throws Exception {
        testArchiveRotate(".yyyy-MM-dd", ".gz", false);
        testArchiveRotate(".yyyy-MM-dd", ".gz", true);
    }

    @Test
    public void testArchiveRotateZip() throws Exception {
        testArchiveRotate(".yyyy-MM-dd", ".zip", false);
        testArchiveRotate(".yyyy-MM-dd", ".zip", true);
    }

    @Test
    public void testArchiveRotateSizeOnlyGzip() throws Exception {
        testArchiveRotate(null, ".gz", false);
        testArchiveRotate(null, ".gz", true);
    }

    @Test
    public void testArchiveRotateSizeOnlyZip() throws Exception {
        testArchiveRotate(null, ".zip", false);
        testArchiveRotate(null, ".zip", true);
    }

    @Test
    @BMRule(name = "Test failed rotated", targetClass = "java.nio.file.Files", targetMethod = "move", targetLocation = "AT ENTRY", condition = "$2.getFileName().toString().equals(\"rotating-file-handler.log.2\")", action = "throw new IOException(\"Fail on purpose\")")
    public void testFailedRotate() throws Exception {
        final PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setErrorManager(AssertingErrorManager.of(ErrorManager.GENERIC_FAILURE));
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(5);
        handler.setFile(logFile.toFile());

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // The log file should exist, as should one rotated file since we fail the rotation on the second rotate
        Assertions.assertTrue(Files.exists(logFile), () -> String.format("Expected log file %s to exist", logFile));
        final Path rotatedFile = resolvePath(FILENAME + ".1");
        Assertions.assertTrue(Files.exists(rotatedFile), () -> String.format("Expected rotated file %s to exist", rotatedFile));

        // The last line of the log file should end with "99" as it should be the last record
        final List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        final String lastLine = lines.get(lines.size() - 1);
        Assertions.assertTrue(lastLine.endsWith("99"), "Expected the last line to end with 99: " + lastLine);
    }

    private void testArchiveRotate(final String dateSuffix, final String archiveSuffix, final boolean rotateOnBoot)
            throws Exception {
        final String currentDate = dateSuffix == null ? "" : LocalDate.now().format(DateTimeFormatter.ofPattern(dateSuffix));
        PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(2);
        handler.setRotateOnBoot(rotateOnBoot);
        handler.setFile(logFile.toFile());
        handler.setSuffix((dateSuffix == null ? "" : dateSuffix) + archiveSuffix);
        // Set append to true to ensure the rotated file is overwritten
        handler.setAppend(true);

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // We should end up with 3 files, 2 rotated and the default log
        final Path logDir = logDirectory();
        final Path path1 = logDir.resolve(FILENAME + currentDate + ".1" + archiveSuffix);
        final Path path2 = logDir.resolve(FILENAME + currentDate + ".2" + archiveSuffix);
        Assertions.assertTrue(Files.exists(logDir));
        Assertions.assertTrue(Files.exists(path1));
        Assertions.assertTrue(Files.exists(path2));

        // Validate the files are not empty and the compressed file contains at least one log record
        if (archiveSuffix.endsWith(".gz")) {
            validateGzipContents(path1, "Test message:");
            validateGzipContents(path2, "Test message:");
        } else if (archiveSuffix.endsWith(".zip")) {
            validateZipContents(path1, logFile.getFileName().toString(), "Test message:");
            validateZipContents(path2, logFile.getFileName().toString(), "Test message:");
        } else {
            Assertions.fail("Unknown archive suffix: " + archiveSuffix);
        }

        compareArchiveContents(path1, path2, logFile.getFileName().toString());
    }

    private void testPeriodicAndSizeRotate0(int handlerPeriod, int logMessagePeriod, boolean testSize) throws Exception {
        int logCount = 1;
        if (testSize) {
            logCount = 100;
        }
        final long rotateSize = 1024L;
        final SimpleDateFormat fmt = periodFormatMap.get(handlerPeriod);
        final Calendar cal = Calendar.getInstance();
        String extension = "." + fmt.format(cal.getTimeInMillis());

        PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        // Enough to not rotate
        handler.setRotateSize(rotateSize);
        handler.setMaxBackupIndex(2);
        handler.setSuffix("." + fmt.toPattern());
        handler.setFile(logFile.toFile());

        // Write a record
        for (int i = 0; i < logCount; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        Path rotatedFile1, rotatedFile2;
        if (testSize) {
            rotatedFile1 = resolvePath(FILENAME + extension + ".1");
            rotatedFile2 = resolvePath(FILENAME + extension + ".2");

            // File should have been rotated
            Assertions.assertTrue(Files.exists(logFile));
            Assertions.assertTrue(Files.exists(rotatedFile1),
                    String.format("Log should have rotated, but it did not%n%s", rotatedFile1));
            Assertions.assertTrue(Files.exists(rotatedFile2),
                    String.format("Log should have rotated, but it did not%n%s", rotatedFile2));
        }

        // Increase the calender to force a rotation
        cal.add(logMessagePeriod, 1);

        // Write a new record which should result in a rotation
        for (int i = 0; i < logCount; i++) {
            ExtLogRecord record = createLogRecord("Test message: %d", i);
            record.setMillis(cal.getTimeInMillis());
            handler.publish(record);
        }

        handler.close();

        if (testSize) {
            // The extension name will be the new period since the size rotation
            // has happened since the date rotation
            extension = "." + fmt.format(cal.getTimeInMillis());
            rotatedFile1 = resolvePath(FILENAME + extension + ".1");
            rotatedFile2 = resolvePath(FILENAME + extension + ".2");
        } else {
            // The extension name will still be the old period since no size rotation
            // has happened to bump up the new period
            rotatedFile1 = resolvePath(FILENAME + extension);
            rotatedFile2 = resolvePath(FILENAME + extension);
        }

        Assertions.assertTrue(Files.exists(logFile));
        Assertions.assertTrue(Files.size(logFile) > 0L);
        ErrorCreator errorCreator = new ErrorCreator(handlerPeriod, logMessagePeriod, testSize);
        if (shouldRotate(logMessagePeriod, handlerPeriod, testSize)) {
            Assertions.assertTrue(Files.exists(rotatedFile1), errorCreator.create(true, rotatedFile1));
            Assertions.assertTrue(Files.exists(rotatedFile2), errorCreator.create(true, rotatedFile2));
            Assertions.assertTrue(Files.size(rotatedFile1) > 0L);
            Assertions.assertTrue(Files.size(rotatedFile2) > 0L);
        } else {
            Assertions.assertFalse(Files.exists(rotatedFile1), errorCreator.create(true, rotatedFile1));
            Assertions.assertFalse(Files.exists(rotatedFile2), errorCreator.create(true, rotatedFile2));
            Assertions.assertFalse(Files.size(rotatedFile1) > 0L);
            Assertions.assertFalse(Files.size(rotatedFile2) > 0L);
        }
    }

    private boolean shouldRotate(int logMessagePeriod, int handlerPeriod, boolean testSize) {
        if (testSize) {
            return true;
        }

        // If the time period added to the log message is greater than the time period specified
        // for file rotation, then we should expect the log to have rotated
        // **The bigger the time period, the smaller the int**

        if (logMessagePeriod > handlerPeriod) {
            Calendar cal = Calendar.getInstance();
            if (isPeriodOneLess(logMessagePeriod, handlerPeriod) &&
                    cal.get(logMessagePeriod) == cal.getActualMaximum(logMessagePeriod)) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    // This is really tricky.  If the test suite is run when the log message
    // period is at is cal.getActualMaximum(), then when you increment by one,
    // you should expect a rollover, but if you simply check to see if the log
    // message period is less than the handler period, this won't be the case.
    // To address this, you need to know if the log message period rollover
    // will affect whether or not the log will actually roll over.  That's only
    // the case if the log message's period is logically one smaller than the
    // handler's period.
    private static boolean isPeriodOneLess(int period1, int period2) {
        return (supportedPeriods.indexOf(period1) - supportedPeriods.indexOf(period2)) == 1;
    }

    private final class ErrorCreator {
        private int handlerPeriod, logMessagePeriod;
        private boolean testSize;

        public ErrorCreator(int handlerPeriod, int logMessagePeriod, boolean testSize) {
            this.handlerPeriod = handlerPeriod;
            this.logMessagePeriod = logMessagePeriod;
            this.testSize = testSize;
        }

        public String create(boolean expectRotation, Path log) throws Exception {
            StringBuilder builder = new StringBuilder();
            if (expectRotation) {
                builder.append("Expected log rotation, but it didn't happen").append(System.lineSeparator());
            } else {
                builder.append("Expected NO log rotation, but it happened anyways").append(System.lineSeparator());
            }

            builder.append("Handler: ").append(periodFormatMap.get(handlerPeriod).toPattern());
            builder.append(" ; ");
            builder.append("Message: ").append(periodFormatMap.get(logMessagePeriod).toPattern());
            builder.append(" ; ");
            builder.append("testSize=").append(testSize);

            builder.append(System.lineSeparator()).append("Checking for log file here: ");
            builder.append(log).append(System.lineSeparator());
            builder.append("List of log files:").append(System.lineSeparator());
            try (Stream<Path> paths = Files.walk(logDirectory())) {
                paths.forEach(path -> {
                    builder.append('\t').append(path).append(System.lineSeparator());
                });
            }
            builder.append("-- End of listing --");
            return builder.toString();
        }
    }
}
