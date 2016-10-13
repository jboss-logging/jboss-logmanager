/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.formatters;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtLogRecord.FormatStyle;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.formatters.StructuredFormatter.Keys;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class XmlFormatterTest extends AbstractTest {

    @Test
    public void testFormat() throws Exception {
        final XmlFormatter formatter = new XmlFormatter();
        formatter.setPrintDetails(true);
        ExtLogRecord record = createLogRecord("Test formatted %s", "message");
        compare(record, formatter);

        record = createLogRecord("Test Message");
        compare(record, formatter);

        record = createLogRecord(Level.ERROR, "Test formatted %s", "message");
        record.setLoggerName("org.jboss.logmanager.ext.test");
        record.setMillis(System.currentTimeMillis());
        record.setThrown(new RuntimeException("Test Exception"));
        record.putMdc("testMdcKey", "testMdcValue");
        record.setNdc("testNdc");
        compare(record, formatter);
    }

    private static int getInt(final XMLStreamReader reader) throws XMLStreamException {
        final String value = getString(reader, true);
        if (value != null) {
            return Integer.parseInt(value);
        }
        return 0;
    }

    private static long getLong(final XMLStreamReader reader) throws XMLStreamException {
        final String value = getString(reader, true);
        if (value != null) {
            return Long.parseLong(value);
        }
        return 0L;
    }

    private static String getString(final XMLStreamReader reader) throws XMLStreamException {
        return getString(reader, true);
    }

    private static String getString(final XMLStreamReader reader, boolean sanitize) throws XMLStreamException {
        final int state = reader.next();
        if (state == XMLStreamConstants.END_ELEMENT) {
            return null;
        }
        if (state == XMLStreamConstants.CHARACTERS) {
            final String text = reader.getText();
            if (sanitize) {
                return sanitize(text);
            }
            return text;
        }
        throw new IllegalStateException("No text");
    }

    private static List<String> getArray(final XMLStreamReader reader, final String arrayKey) throws XMLStreamException {
        final List<String> result = new ArrayList<String>();
        int state;
        while (reader.hasNext() && (state = reader.next()) != XMLStreamConstants.END_ELEMENT) {
            if (state == XMLStreamConstants.CHARACTERS) {
                String text = sanitize(reader.getText());
                if (text == null || text.isEmpty()) continue;
                Assert.fail(String.format("Invalid text found: %s", text));
            }
            Assert.assertEquals(arrayKey, reader.getLocalName());
            Assert.assertTrue(reader.hasNext());
            final String value = getString(reader, true);
            Assert.assertNotNull(value);
            result.add(value);
        }
        return result;
    }

    private static Map<String, String> getMap(final XMLStreamReader reader) throws XMLStreamException {
        if (reader.hasNext()) {
            int state;
            final Map<String, String> result = new LinkedHashMap<>();
            while (reader.hasNext() && (state = reader.next()) != XMLStreamConstants.END_ELEMENT) {
                String name = (state == XMLStreamConstants.START_ELEMENT ? reader.getLocalName() : null);
                if (state == XMLStreamConstants.CHARACTERS) {
                    String text = sanitize(reader.getText());
                    if (text == null || text.isEmpty()) continue;
                    Assert.fail(String.format("Invalid text found: %s", text));
                }
                final String key = reader.getLocalName();
                Assert.assertTrue(reader.hasNext());
                final String value = getString(reader, true);
                Assert.assertNotNull(value);
                result.put(key, value);
            }
            return result;
        }
        return Collections.emptyMap();
    }

    public static List<String> convert(final Object[] objects) {
        if (objects == null) return Collections.emptyList();
        final List<String> result = new ArrayList<>();
        for (Object obj : objects) {
            result.add(String.valueOf(obj));
        }
        return result;
    }

    private static String sanitize(final String value) {
        return value.replaceAll("\n", "").trim();
    }

    private static void compare(final ExtLogRecord record, final ExtFormatter formatter) throws XMLStreamException {
        compare(record, formatter.format(record));
    }

    private static void compare(final ExtLogRecord record, final String xmlString) throws XMLStreamException {
        final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        final XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xmlString));

        boolean inException = false;
        while (reader.hasNext()) {
            final int state = reader.next();
            if (state == XMLStreamConstants.END_ELEMENT && inException) {
                inException = false;
            }
            if (state == XMLStreamConstants.START_ELEMENT) {
                final String localName = reader.getLocalName();
                if (localName.equals(Keys.EXCEPTION)) {
                    inException = true;// TODO (jrp) stack trace may need to be validated
                } else if (localName.equals(StructuredFormatter.Keys.FORMAT_STYLE_KEY)) {
                    Assert.assertEquals(record.getFormatStyle(), FormatStyle.valueOf(getString(reader)));
                } else if (localName.equals(StructuredFormatter.Keys.FORMATTED_MESSAGE_KEY)) {
                    Assert.assertEquals(record.getFormattedMessage(), getString(reader));
                } else if (localName.equals(StructuredFormatter.Keys.LEVEL_KEY)) {
                    Assert.assertEquals(record.getLevel(), Level.parse(getString(reader)));
                } else if (localName.equals(StructuredFormatter.Keys.LOGGER_CLASS_NAME_KEY)) {
                    Assert.assertEquals(record.getLoggerClassName(), getString(reader));
                } else if (localName.equals(StructuredFormatter.Keys.LOGGER_NAME_KEY)) {
                    Assert.assertEquals(record.getLoggerName(), getString(reader));
                } else if (localName.equals(StructuredFormatter.Keys.MDC_KEY)) {
                    compareMap(record.getMdcCopy(), getMap(reader));
                } else if (!inException && localName.equals(StructuredFormatter.Keys.MESSAGE_KEY)) {
                    Assert.assertEquals(record.getMessage(), getString(reader));
                } else if (localName.equals(StructuredFormatter.Keys.NDC_KEY)) {
                    final String value = getString(reader);
                    Assert.assertEquals(record.getNdc(), (value == null ? "" : value));
                } else if (localName.equals(StructuredFormatter.Keys.PARAMETERS_KEY)) {
                    final List<String> array = getArray(reader, StructuredFormatter.Keys.PARAMETER_KEY);
                    Assert.assertTrue(String.format("%n%s%n%s", convert(record.getParameters()), array),
                            convert(record.getParameters()).containsAll(array));
                } else if (localName.equals(StructuredFormatter.Keys.SEQUENCE_KEY)) {
                    Assert.assertEquals(record.getSequenceNumber(), getLong(reader));
                } else if (localName.equals(StructuredFormatter.Keys.SOURCE_CLASS_NAME_KEY)) {
                    Assert.assertEquals(record.getSourceClassName(), getString(reader));
                } else if (localName.equals(StructuredFormatter.Keys.SOURCE_FILE_NAME_KEY)) {
                    Assert.assertEquals(record.getSourceFileName(), getString(reader));
                } else if (localName.equals(StructuredFormatter.Keys.SOURCE_LINE_NUMBER_KEY)) {
                    Assert.assertEquals(record.getSourceLineNumber(), getInt(reader));
                } else if (localName.equals(StructuredFormatter.Keys.SOURCE_METHOD_NAME_KEY)) {
                    Assert.assertEquals(record.getSourceMethodName(), getString(reader));
                } else if (localName.equals(StructuredFormatter.Keys.THREAD_ID_KEY)) {
                    Assert.assertEquals(record.getThreadID(), getInt(reader));
                } else if (localName.equals(StructuredFormatter.Keys.THREAD_NAME_KEY)) {
                    Assert.assertEquals(record.getThreadName(), getString(reader));
                } else if (localName.equals(StructuredFormatter.Keys.TIMESTAMP_KEY)) {
                    final SimpleDateFormat sdf = new SimpleDateFormat(StructuredFormatter.DEFAULT_DATE_FORMAT);
                    Assert.assertEquals(sdf.format(new Date(record.getMillis())), getString(reader));
                }
            }
        }
    }

    private static void compareMap(final Map<String, String> m1, final Map<String, String> m2) {
        Assert.assertEquals("Map sizes do not match", m1.size(), m2.size());
        for (String key : m1.keySet()) {
            Assert.assertTrue("Second map does not contain key " + key, m2.containsKey(key));
            Assert.assertEquals(m1.get(key), m2.get(key));
        }
    }
}
