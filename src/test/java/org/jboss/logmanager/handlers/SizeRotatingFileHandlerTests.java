/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.logmanager.handlers;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
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
}
