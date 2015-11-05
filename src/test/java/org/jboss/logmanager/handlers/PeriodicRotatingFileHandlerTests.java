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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PeriodicRotatingFileHandlerTests extends AbstractHandlerTest {

    private final static String FILENAME = "periodic-rotating-file-handler.log";

    private final String logFile = BASE_LOG_DIR.toString() + File.separator + FILENAME;

    private final SimpleDateFormat rotateFormatter = new SimpleDateFormat(".yyyy-MM-dd");
    private PeriodicRotatingFileHandler handler;

    @Before
    public void createHandler() throws FileNotFoundException {
        // Create the handler
        File logFile = new File(this.logFile);
        handler = new PeriodicRotatingFileHandler(logFile, rotateFormatter.toPattern(), false);
        handler.setFormatter(FORMATTER);
    }

    @After
    public void closeHandler() {
        handler.close();
        handler = null;
    }

    @Test
    public void testRotate() throws Exception {
        final Calendar cal = Calendar.getInstance();
        final String rotatedPath = BASE_LOG_DIR.toString() + File.separator + FILENAME + rotateFormatter.format(cal.getTime());
        File rotatedFile = new File(rotatedPath);
        testRotate(cal, rotatedFile);
    }

    @Test
    public void testOverwriteRotate() throws Exception {
        final Calendar cal = Calendar.getInstance();
        final String rotatedPath = BASE_LOG_DIR.toString() + File.separator + FILENAME + rotateFormatter.format(cal.getTime());

        // Create the rotated file to ensure at some point it gets overwritten
        File rotatedFile = new File(rotatedPath);

        if (rotatedFile.exists()) {
            rotatedFile.delete();
        }

        FileWriter fwriter = null;
        BufferedWriter writer = null;
        try {
            fwriter = new FileWriter(rotatedFile.getAbsolutePath());
            writer = new BufferedWriter(fwriter);
            writer.write("Adding data to the file");
        } finally {
            writer.close();
        }
        testRotate(cal, rotatedFile);
    }

    private void testRotate(final Calendar cal, final File rotatedFile) throws Exception {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        File logFile = new File(this.logFile);

        final String currentDate = sdf.format(cal.getTime());

        // Create a log message to be logged
        ExtLogRecord record = createLogRecord(Level.INFO, "Date: %s", currentDate);
        handler.publish(record);

        Assert.assertTrue("File '" + logFile.getName() + "' does not exist", logFile.exists());

        // Read the contents of the log file and ensure there's only one line and that like
        List<String> lines = readAllLines(logFile);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));

        // Create a new record, increment the day by one and validate
        cal.add(Calendar.DAY_OF_MONTH, 1);
        final String nextDate = sdf.format(cal.getTime());
        record = createLogRecord(Level.INFO, "Date: %s", nextDate);
        record.setMillis(cal.getTimeInMillis());
        handler.publish(record);

        // Read the contents of the log file and ensure there's only one line and that like
        lines = readAllLines(logFile);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + nextDate, lines.get(0).contains(nextDate));

        // The file should have been rotated as well
        Assert.assertTrue("The rotated file '" + rotatedFile.getName() + "' does not exist", rotatedFile.exists());
        lines = readAllLines(rotatedFile);
        Assert.assertEquals("More than 1 line found", 1, lines.size());
        Assert.assertTrue("Expected the line to contain the date: " + currentDate, lines.get(0).contains(currentDate));
    }

    private List<String> readAllLines(File f) throws FileNotFoundException, IOException {
        List<String> list = new ArrayList();

        BufferedReader br = new BufferedReader(new FileReader(f));
        String line;
        while ((line = br.readLine()) != null) {
            list.add(line);
        }

        return list;
    }
}
