/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(BMUnitRunner.class)
public class PeriodicSizeRotatingFileHandlerTests extends AbstractHandlerTest {
    private final static String FILENAME = "rotating-file-handler.log";

    private final File logFile = new File(BASE_LOG_DIR, FILENAME);

    private static final List<Integer> supportedPeriods = new ArrayList<Integer>();
    private static final Map<Integer, SimpleDateFormat> periodFormatMap =
        new HashMap<Integer, SimpleDateFormat>();

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
        handler.setFile(logFile);

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // We should end up with 3 files, 2 rotated and the default log
        final File file1 = new File(BASE_LOG_DIR, FILENAME + extension + ".1");
        final File file2 = new File(BASE_LOG_DIR, FILENAME + extension + ".2");
        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(file1.exists());
        Assert.assertTrue(file2.exists());

        // Clean up files
        file1.delete();
        file2.delete();
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
        handler.setFile(logFile);
        final File rotatedFile = new File(BASE_LOG_DIR, FILENAME + extension + ".1");

        // The rotated file should not exist
        Assert.assertFalse("Rotated file should not exist", rotatedFile.exists());

        // Log a few records
        for (int i = 0; i < 5; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        // Close the handler and create a new one
        handler.close();
        final long size = logFile.length();
        handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(5000L);
        handler.setMaxBackupIndex(1);
        handler.setSuffix("." + fmt.toPattern());
        handler.setRotateOnBoot(true);
        handler.setFile(logFile);

        // The rotated file should exist
        Assert.assertTrue("Rotated file should exist", rotatedFile.exists());

        // Rotated file size should match the size of the previous file
        Assert.assertEquals(size, rotatedFile.length());

        // Log a few records
        for (int i = 0; i < 10; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // File should have been rotated
        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(rotatedFile.exists());

        // Neither file should be empty
        Assert.assertTrue(logFile.length() > 0L);
        Assert.assertTrue(rotatedFile.length() > 0L);

        // Clean up files
        rotatedFile.delete();
    }

    @Test
    @Ignore("LOGMGR-82")
    public void testPeriodicAndSizeRotate() throws Exception {
        for (int i=0; i < supportedPeriods.size(); i++) {
            //To cut down on unnecessary testing, let's only test
            //the periods +/- two from this period
            int j = i-2;
            if (j < 0) j = 0;

            int handlerPeriod = supportedPeriods.get(i);
            for (; j <= i+2; j++) {
                if (j >= supportedPeriods.size()) break;
                int logMessagePeriod = supportedPeriods.get(j);
                testPeriodicAndSizeRotate0(handlerPeriod, logMessagePeriod, true);
                testPeriodicAndSizeRotate0(handlerPeriod, logMessagePeriod, false);
            }
        }
    }

    @Test
    @BMRule(name = "Test failed rotated",
            targetClass = "java.nio.file.Files",
            targetMethod = "move",
            targetLocation = "AT ENTRY",
            condition = "$2.getFileName().toString().equals(\"rotating-file-handler.log.2\")",
            action = "throw new IOException(\"Fail on purpose\")")
    public void testFailedRotate() throws Exception {
        final PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(5);
        handler.setFile(logFile);

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // The log file should exist, as should one rotated file since we fail the rotation on the second rotate
        Assert.assertTrue(String.format("Expected log file %s to exist", logFile), logFile.exists());
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + ".1");
        Assert.assertTrue(String.format("Expected rotated file %s to exist", rotatedFile), Files.exists(rotatedFile));

        // The last line of the log file should end with "99" as it should be the last record
        final List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
        final String lastLine = lines.get(lines.size() - 1);
        Assert.assertTrue("Expected the last line to end with 99: " + lastLine, lastLine.endsWith("99"));
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
        handler.setFile(logFile);

        // Write a record
        for (int i = 0; i < logCount; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        File rotatedFile1, rotatedFile2;
        if (testSize) {
            rotatedFile1 = new File(BASE_LOG_DIR, FILENAME + extension + ".1");
            rotatedFile2 = new File(BASE_LOG_DIR, FILENAME + extension + ".2");

            // File should have been rotated
            String message = "Log should have rotated, but it did not\n";
            Assert.assertTrue(logFile.exists());
            Assert.assertTrue(message + rotatedFile1.getPath(), rotatedFile1.exists());
            Assert.assertTrue(message + rotatedFile2.getPath(), rotatedFile2.exists());
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
            rotatedFile1 = new File(BASE_LOG_DIR, FILENAME + extension + ".1");
            rotatedFile2 = new File(BASE_LOG_DIR, FILENAME + extension + ".2");
        } else {
            // The extension name will still be the old period since no size rotation
            // has happened to bump up the new period
            rotatedFile1 = new File(BASE_LOG_DIR, FILENAME + extension);
            rotatedFile2 = new File(BASE_LOG_DIR, FILENAME + extension);
        }

        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(logFile.length() > 0L);

        try {
            ErrorCreator errorCreator = new ErrorCreator(handlerPeriod, logMessagePeriod, testSize);
            if (shouldRotate(logMessagePeriod, handlerPeriod, testSize)) {
                Assert.assertTrue(errorCreator.create(true, rotatedFile1), rotatedFile1.exists());
                Assert.assertTrue(errorCreator.create(true, rotatedFile2), rotatedFile2.exists());
                Assert.assertTrue(rotatedFile1.length() > 0L);
                Assert.assertTrue(rotatedFile2.length() > 0L);
            } else {
                Assert.assertFalse(errorCreator.create(false, rotatedFile1), rotatedFile1.exists());
                Assert.assertFalse(errorCreator.create(false, rotatedFile2), rotatedFile2.exists());
                Assert.assertFalse(rotatedFile1.length() > 0L);
                Assert.assertFalse(rotatedFile2.length() > 0L);
            }
        } finally {
            for (String logFile : BASE_LOG_DIR.list()) {
                new File(BASE_LOG_DIR + File.separator + logFile).delete();
            }
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

    private class ErrorCreator {
        private int handlerPeriod, logMessagePeriod;
        private boolean testSize;

        public ErrorCreator(int handlerPeriod, int logMessagePeriod, boolean testSize) {
            this.handlerPeriod = handlerPeriod;
            this.logMessagePeriod = logMessagePeriod;
            this.testSize = testSize;
        }

        public String create(boolean expectRotation, File log) throws Exception {
              StringBuilder builder = new StringBuilder();
              if (expectRotation) {
                  builder.append("Expected log rotation, but it didn't happen\n");
              } else {
                  builder.append("Expected NO log rotation, but it happened anyways\n");
              }

              builder.append("Handler: " + periodFormatMap.get(handlerPeriod).toPattern());
              builder.append(" ; ");
              builder.append("Message: " + periodFormatMap.get(logMessagePeriod).toPattern());
              builder.append(" ; ");
              builder.append("testSize=" + testSize);

              builder.append("\nChecking for log file here: ");
              builder.append(log.getPath() + "\n");
              builder.append("List of log files:\n");
              for (String f : BASE_LOG_DIR.list()) {
                  builder.append("\t" + f + "\n");
              }
              builder.append("-- End of listing --");
              return builder.toString();
        }
    }
}
