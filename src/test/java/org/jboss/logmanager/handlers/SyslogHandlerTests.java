/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.logging.Level;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.SyslogHandler.SyslogType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SyslogHandlerTests {
    private static final byte[] UTF_8_BOM = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};
    private static final String BOM = new String(UTF_8_BOM);
    private static final String MSG = "This is a test message";
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 10999;

    private SyslogHandler handler;

    @Before
    public void setupHandler() throws Exception {
        handler = new SyslogHandler(HOSTNAME, PORT);
        handler.setFormatter(new PatternFormatter("%s"));
    }

    @After
    public void closeHandler() throws Exception {
        // Close the handler
        handler.flush();
        handler.close();
    }

    @Test
    public void testRFC5424Tcp() throws Exception {
        // Setup the handler
        handler.setSyslogType(SyslogType.RFC5424);
        handler.setMessageDelimiter("\n");
        handler.setUseMessageDelimiter(true);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.setOutputStream(out);

        final Calendar cal = getCalendar();
        // Create the record
        handler.setHostname("test");
        ExtLogRecord record = createRecord(cal, MSG);
        String expectedMessage = "<14>1 2012-01-09T04:39:22.000" + calculateTimeZone(cal) + " test java " + handler.getPid() + " - - " + BOM + MSG + '\n';
        handler.publish(record);
        Assert.assertEquals(expectedMessage, out.toString());

        // Create the record
        out.reset();
        record = createRecord(cal, MSG);
        handler.setHostname(null);
        handler.setAppName(null);
        expectedMessage = "<14>1 2012-01-09T04:39:22.000" + calculateTimeZone(cal) + " - - " + handler.getPid() + " - - " + BOM + MSG + '\n';
        handler.publish(record);
        Assert.assertEquals(expectedMessage, out.toString());

        out.reset();
        cal.set(Calendar.DAY_OF_MONTH, 31);
        record = createRecord(cal, MSG);
        handler.setHostname("test");
        handler.setAppName("java");
        expectedMessage = "<14>1 2012-01-31T04:39:22.000" + calculateTimeZone(cal) + " test java " + handler.getPid() + " - - " + BOM + MSG + '\n';
        handler.publish(record);
        Assert.assertEquals(expectedMessage, out.toString());
    }

    @Test
    public void testRFC31644Format() throws Exception {
        // Setup the handler
        handler.setSyslogType(SyslogType.RFC3164);
        handler.setHostname("test");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.setOutputStream(out);

        final Calendar cal = getCalendar();

        // Create the record
        ExtLogRecord record = createRecord(cal, MSG);
        handler.publish(record);
        String expectedMessage = "<14>Jan  9 04:39:22 test java[" + handler.getPid() + "]: " + MSG;
        Assert.assertEquals(expectedMessage, out.toString());

        out.reset();
        cal.set(Calendar.DAY_OF_MONTH, 31);
        record = createRecord(cal, MSG);
        handler.publish(record);
        expectedMessage = "<14>Jan 31 04:39:22 test java[" + handler.getPid() + "]: " + MSG;
        Assert.assertEquals(expectedMessage, out.toString());
    }

    @Test
    public void testOctetCounting() throws Exception {
        // Setup the handler
        handler.setSyslogType(SyslogType.RFC5424);
        handler.setUseMessageDelimiter(false);
        handler.setUseCountingFraming(true);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.setOutputStream(out);

        final Calendar cal = getCalendar();
        // Create the record
        handler.setHostname("test");
        ExtLogRecord record = createRecord(cal, MSG);
        String expectedMessage = "<14>1 2012-01-09T04:39:22.000" + calculateTimeZone(cal) + " test java " + handler.getPid() + " - - " + BOM + MSG;
        expectedMessage = expectedMessage.getBytes().length + " " + expectedMessage;
        handler.publish(record);
        Assert.assertEquals(expectedMessage, out.toString());
    }

    @Test
    public void testTruncation() throws Exception {
        // Setup the handler
        handler.setSyslogType(SyslogType.RFC5424);
        handler.setUseMessageDelimiter(false);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.setOutputStream(out);

        final Calendar cal = getCalendar();
        // Create the record
        handler.setHostname("test");
        final String part1 = "This is a longer message and should be truncated after this.";
        final String part2 = "Truncated portion of the message that will not be shown in.";
        final String message = part1 + " " + part2;

        final String header = "<14>1 2012-01-09T04:39:22.000" + calculateTimeZone(cal) + " test java " + handler.getPid() + " - - " + BOM;

        handler.setMaxLength(header.getBytes().length + part1.getBytes().length);
        handler.setTruncate(true);

        ExtLogRecord record = createRecord(cal, message);
        String expectedMessage = header + part1;
        handler.publish(record);
        Assert.assertEquals(expectedMessage, out.toString());

        out.reset();
        // Wrap a message
        handler.setTruncate(false);
        handler.publish(record);
        // Extra space from message
        expectedMessage = header + part1 + header + " " + part2;
        Assert.assertEquals(expectedMessage, out.toString());
    }

    @Test
    public void testMultibyteTruncation() throws Exception {
        String part1 = "This is a longer message and should be truncated after this  À あ";
        String part2 = "あ À Truncated portion of the message that will not be shown in.";
        testMultibyteTruncation(part1, part2, 1);

        // Test some characters with surrogates
        part1 = "Truncate after double byte 𥹖";
        part2 = "second message 𥹖";
        testMultibyteTruncation(part1, part2, 2);

    }

    private void testMultibyteTruncation(final String part1, final String part2, final int charsToTruncate) throws Exception {
        // Setup the handler
        handler.setSyslogType(SyslogType.RFC5424);
        handler.setUseMessageDelimiter(false);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.setOutputStream(out);

        final Calendar cal = getCalendar();
        // Create the record
        handler.setHostname("test");
        String message = part1 + " " + part2;

        final String header = "<14>1 2012-01-09T04:39:22.000" + calculateTimeZone(cal) + " test java " + handler.getPid() + " - - " + BOM;

        handler.setMaxLength(header.getBytes("utf-8").length + part1.getBytes("utf-8").length);
        handler.setTruncate(true);

        ExtLogRecord record = createRecord(cal, message);
        String expectedMessage = header + part1;
        handler.publish(record);
        Assert.assertTrue(String.format("Expected: %s:%n Received: %s", expectedMessage, out.toString()), Arrays.equals(expectedMessage.getBytes("utf-8"), out.toByteArray()));

        out.reset();
        // Wrap a message
        handler.setTruncate(false);
        handler.publish(record);
        // Extra space from message
        expectedMessage = header + part1 + header + " " + part2;
        Assert.assertTrue(String.format("Expected: %s:%n Received: %s", expectedMessage, out.toString()), Arrays.equals(expectedMessage.getBytes("utf-8"), out.toByteArray()));

        // Reset out, write the message with a maximum length of the current length minus 1 to ensure the multi-byte character was not written at the end
        out.reset();
        handler.setTruncate(true);
        handler.setMaxLength(header.getBytes("utf-8").length + part1.getBytes("utf-8").length - 1);
        expectedMessage = header + part1.substring(0, part1.length() - charsToTruncate);
        handler.publish(record);
        Assert.assertTrue(String.format("Expected: %s:%n Received: %s", expectedMessage, out.toString()), Arrays.equals(expectedMessage.getBytes("utf-8"), out.toByteArray()));
    }

    private static ExtLogRecord createRecord(final Calendar cal, final String message) {
        final String loggerName = SyslogHandlerTests.class.getName();
        final ExtLogRecord record = new ExtLogRecord(Level.INFO, message, loggerName);
        record.setMillis(cal.getTimeInMillis());
        return record;
    }

    private static Calendar getCalendar() {
        final Calendar cal = Calendar.getInstance();
        cal.set(2012, Calendar.JANUARY, 9, 4, 39, 22);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private static String calculateTimeZone(final Calendar cal) {
        final int tz = cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET);
        final StringBuilder buffer = new StringBuilder();
        if (tz == 0) {
            buffer.append("+00:00");
        } else {
            int tzMinutes = tz / 60000; // milliseconds to minutes
            if (tzMinutes < 0) {
                tzMinutes = -tzMinutes;
                buffer.append('-');
            } else {
                buffer.append('+');
            }
            final int tzHour = tzMinutes / 60; // minutes to hours
            tzMinutes -= tzHour * 60; // subtract hours from minutes in minutes
            if (tzHour < 10) {
                buffer.append(0);
            }
            buffer.append(tzHour).append(':');
            if (tzMinutes < 10) {
                buffer.append(0);
            }
            buffer.append(tzMinutes);
        }
        return buffer.toString();
    }
}
