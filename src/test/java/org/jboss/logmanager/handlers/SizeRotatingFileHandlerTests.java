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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.ErrorManager;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(BMUnitRunner.class)
public class SizeRotatingFileHandlerTests extends AbstractHandlerTest {
    private final static String FILENAME = "rotating-file-handler.log";

    private final File logFile = new File(BASE_LOG_DIR, FILENAME);

    @Test
    public void testSizeRotate() throws Exception {
        final SizeRotatingFileHandler handler = new SizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(2);
        handler.setFile(logFile);

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // We should end up with 3 files, 2 rotated and the default log
        final File file1 = new File(BASE_LOG_DIR, FILENAME + ".1");
        final File file2 = new File(BASE_LOG_DIR, FILENAME + ".2");
        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(file1.exists());
        Assert.assertTrue(file2.exists());

        // Clean up files
        file1.delete();
        file2.delete();
    }

    @Test
    public void testSuffixSizeRotate() throws Exception {
        final SizeRotatingFileHandler handler = new SizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(2);
        handler.setFile(logFile);
        handler.setSuffix(".yyyy-MM-dd");

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        final SimpleDateFormat sdf = new SimpleDateFormat(".yyyy-MM-dd");
        // Note that it is possible the suffix won't match the pattern if a rotation happens after midnight.
        final String suffix = sdf.format(new Date());

        // We should end up with 3 files, 2 rotated and the default log
        final File file1 = new File(BASE_LOG_DIR, FILENAME + suffix + ".1");
        final File file2 = new File(BASE_LOG_DIR, FILENAME + suffix + ".2");
        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(file1.exists());
        Assert.assertTrue(file2.exists());

        // Clean up files
        file1.delete();
        file2.delete();
    }

    @Test
    public void testBootRotate() throws Exception {
        SizeRotatingFileHandler handler = new SizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        // Enough to not rotate
        handler.setRotateSize(5000L);
        handler.setMaxBackupIndex(1);
        handler.setRotateOnBoot(true);
        handler.setFile(logFile);
        final File rotatedFile = new File(BASE_LOG_DIR, FILENAME + ".1");

        // The rotated file should not exist
        Assert.assertFalse("Rotated file should not exist", rotatedFile.exists());

        // Log a few records
        for (int i = 0; i < 5; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        // Close the handler and create a new one
        handler.close();
        final long size = logFile.length();
        handler = new SizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(5000L);
        handler.setMaxBackupIndex(1);
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
    public void testBootRotateChange() throws Exception {
        SizeRotatingFileHandler handler = new SizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        // Enough to not rotate
        handler.setRotateSize(5000L);
        handler.setMaxBackupIndex(1);
        handler.setFile(logFile);
        final Path rotatedFile = BASE_LOG_DIR.toPath().resolve(FILENAME + ".1");

        // The rotated file should not exist
        Assert.assertTrue("Rotated file should not exist", Files.notExists(rotatedFile));

        // Log a few records
        for (int i = 0; i < 5; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        // Configure the handler to rotate on boot and reset the file
        handler.setRotateOnBoot(true);
        handler.setFile(logFile);

        // Log a few records
        for (int i = 0; i < 10; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // File should have been rotated
        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(Files.exists(rotatedFile));

        // Neither file should be empty
        Assert.assertTrue(logFile.length() > 0L);
        Assert.assertTrue(Files.size(rotatedFile) > 0L);
    }

    @Test
    public void testArchiveRotateGzip() throws Exception {
        testArchiveRotate(".gz", false);
        testArchiveRotate(".gz", true);
    }

    @Test
    public void testArchiveRotateZip() throws Exception {
        testArchiveRotate(".zip", false);
        testArchiveRotate(".zip", true);
    }

    /**
     * Note we only test a failed rotation on the SizeRotatingFileHandler. The type of the rotation, e.g. periodic vs
     * size, shouldn't matter as each uses the same rotation logic in the SuffixRotator.
     */
    @Test
    @BMRule(name = "Test failed rotated", targetClass = "java.nio.file.Files", targetMethod = "move", targetLocation = "AT ENTRY", condition = "$2.getFileName().toString().equals(\"rotating-file-handler.log.2\")", action = "throw new IOException(\"Fail on purpose\")")
    public void testFailedRotate() throws Exception {
        final SizeRotatingFileHandler handler = new SizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setErrorManager(AssertingErrorManager.of(ErrorManager.GENERIC_FAILURE));
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

    private void testArchiveRotate(final String archiveSuffix, final boolean rotateOnBoot) throws Exception {
        final SizeRotatingFileHandler handler = new SizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(2);
        handler.setRotateOnBoot(rotateOnBoot);
        handler.setFile(logFile);
        handler.setSuffix(archiveSuffix);
        // Set append to true to ensure the rotated file is overwritten
        handler.setAppend(true);

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // We should end up with 3 files, 2 rotated and the default log
        final Path logDir = BASE_LOG_DIR.toPath();
        final Path path1 = logDir.resolve(FILENAME + ".1" + archiveSuffix);
        final Path path2 = logDir.resolve(FILENAME + ".2" + archiveSuffix);
        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(Files.exists(path1));
        Assert.assertTrue(Files.exists(path2));

        // Validate the files are not empty and the compressed file contains at least one log record
        if (archiveSuffix.endsWith(".gz")) {
            validateGzipContents(path1, "Test message:");
            validateGzipContents(path2, "Test message:");
        } else if (archiveSuffix.endsWith(".zip")) {
            validateZipContents(path1, logFile.getName(), "Test message:");
            validateZipContents(path2, logFile.getName(), "Test message:");
        } else {
            Assert.fail("Unknown archive suffix: " + archiveSuffix);
        }

        compareArchiveContents(path1, path2, logFile.getName());

        // Clean up files
        Files.deleteIfExists(path1);
        Files.deleteIfExists(path2);
    }
}
