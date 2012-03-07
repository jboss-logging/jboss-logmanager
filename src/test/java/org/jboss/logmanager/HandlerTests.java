/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
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

package org.jboss.logmanager;

import org.testng.annotations.Test;
import static org.testng.AssertJUnit.*;
import org.jboss.logmanager.handlers.WriterHandler;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.jboss.logmanager.handlers.NullHandler;
import org.jboss.logmanager.handlers.FileHandler;
import org.jboss.logmanager.formatters.PatternFormatter;

import java.util.logging.Formatter;

import java.io.StringWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;

@Test
public final class HandlerTests {
    static {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
    }

    private final Formatter testFormatter = new PatternFormatter("%m");

    public void testNullHandler() throws Throwable {
        final NullHandler handler = new NullHandler();
        handler.setLevel(Level.ALL);
        handler.publish(new ExtLogRecord(Level.INFO, "Test message", null));
    }

    private void initHandler(ExtHandler handler) throws UnsupportedEncodingException {
        handler.setFormatter(testFormatter);
        handler.setLevel(Level.ALL);
        handler.setAutoFlush(true);
        handler.setEncoding("utf-8");
    }

    private void testPublish(ExtHandler handler) {
        handler.publish(new ExtLogRecord(Level.INFO, "Test message", null));
    }

    public void testWriterHandler() throws Throwable {
        final WriterHandler handler = new WriterHandler();
        initHandler(handler);
        final StringWriter writer = new StringWriter();
        handler.setWriter(writer);
        testPublish(handler);
        assertEquals("Test message", writer.toString());
    }

    public void testOutputStreamHandler() throws Throwable {
        final OutputStreamHandler handler = new OutputStreamHandler();
        initHandler(handler);
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        handler.setOutputStream(stream);
        testPublish(handler);
        assertEquals("Test message", new String(stream.toByteArray(), "utf-8"));
    }

    public void testFileHandler() throws Throwable {
        final FileHandler handler = new FileHandler();
        initHandler(handler);
        final File tempFile = File.createTempFile("jblm-", ".log");
        try {
            handler.setAppend(true);
            handler.setFile(tempFile);
            testPublish(handler);
            handler.setFile(tempFile);
            testPublish(handler);
            handler.setAppend(false);
            handler.setFile(tempFile);
            testPublish(handler);
            handler.close();
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                final FileInputStream is = new FileInputStream(tempFile);
                try {
                    int r;
                    while ((r = is.read()) != -1) os.write(r);
                    assertEquals("Test message", new String(os.toByteArray(), "utf-8"));
                    tempFile.deleteOnExit();
                } finally {
                    is.close();
                }
            } finally {
                os.close();
            }
        } finally {
            tempFile.delete();
        }
    }

    public void testEnableDisableHandler() throws Throwable {
        final StringListHandler handler = new StringListHandler();
        testPublish(handler);
        assertEquals(1, handler.size());
        handler.disable();
        testPublish(handler);
        assertEquals(1, handler.size());
        handler.enable();
        testPublish(handler);
        assertEquals(2, handler.size());
    }
}
