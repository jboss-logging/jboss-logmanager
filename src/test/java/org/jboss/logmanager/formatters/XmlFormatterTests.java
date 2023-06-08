/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.formatters;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.formatters.StructuredFormatter.Key;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class XmlFormatterTests extends AbstractTest {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    @Test
    public void validate() throws Exception {
        // Configure the formatter
        final XmlFormatter formatter = new XmlFormatter();
        formatter.setPrintNamespace(true);
        formatter.setPrintDetails(true);
        formatter.setExceptionOutputType(StructuredFormatter.ExceptionOutputType.DETAILED_AND_FORMATTED);
        formatter.setMetaData("key1=value1,key2=value2");
        // Create the record get format a message
        final ExtLogRecord record = createLogRecord(Level.ERROR, "Test formatted %s", "message");
        record.setLoggerName("org.jboss.logmanager.test");
        record.setMillis(System.currentTimeMillis());
        record.setThrown(createMultiNestedCause());
        record.putMdc("testMdcKey", "testMdcValue");
        record.setNdc("testNdc");
        final String message = formatter.format(record);

        final ErrorHandler handler = new ErrorHandler() {
            @Override
            public void warning(final SAXParseException exception) throws SAXException {
                fail(exception);
            }

            @Override
            public void error(final SAXParseException exception) throws SAXException {
                fail(exception);
            }

            @Override
            public void fatalError(final SAXParseException exception) throws SAXException {
                fail(exception);
            }

            private void fail(final SAXParseException exception) {
                final StringBuilder failureMessage = new StringBuilder();
                failureMessage.append(exception.getLocalizedMessage())
                        .append(": line ")
                        .append(exception.getLineNumber())
                        .append(" column ")
                        .append(exception.getColumnNumber())
                        .append(System.lineSeparator())
                        .append(message);
                Assertions.fail(failureMessage.toString());
            }
        };

        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setErrorHandler(handler);

        final Schema schema = factory.newSchema(getClass().getResource("/xml-formatter.xsd"));
        final Validator validator = schema.newValidator();
        validator.setErrorHandler(handler);
        validator.setFeature("http://apache.org/xml/features/validation/schema", true);
        validator.validate(new StreamSource(new StringReader(message)));
    }

    @Test
    public void testFormat() throws Exception {
        final XmlFormatter formatter = new XmlFormatter();
        formatter.setPrintDetails(true);
        ExtLogRecord record = createLogRecord("Test formatted %s", "message");
        compare(record, formatter);

        record = createLogRecord("Test Message");
        compare(record, formatter);

        record = createLogRecord(Level.ERROR, "Test formatted %s", "message");
        record.setLoggerName("org.jboss.logmanager.test");
        record.setMillis(System.currentTimeMillis());
        final Throwable t = new RuntimeException("Test cause exception");
        final Throwable dup = new IllegalStateException("Duplicate");
        t.addSuppressed(dup);
        final Throwable cause = new RuntimeException("Test Exception", t);
        dup.addSuppressed(cause);
        cause.addSuppressed(new IllegalArgumentException("Suppressed"));
        cause.addSuppressed(dup);
        record.setThrown(cause);
        record.putMdc("testMdcKey", "testMdcValue");
        record.setNdc("testNdc");
        formatter.setExceptionOutputType(JsonFormatter.ExceptionOutputType.DETAILED_AND_FORMATTED);
        compare(record, formatter);
    }

    @Test
    public void metaData() throws Exception {
        // Configure the formatter
        final var formatter = new XmlFormatter();
        formatter.setMetaData("key1=value1,key2=value2,noValue=");

        final var record = createLogRecord("Test Message");
        final var xml = formatter.format(record);

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();
        final Document doc = builder.parse(new InputSource(new StringReader(xml)));
        final var metaData = doc.getElementsByTagName("metaData");
        Assertions.assertEquals(3, metaData.getLength());
        var item = metaData.item(0);
        Assertions.assertEquals("key1",
                item.getAttributes().getNamedItem("key").getNodeValue(), "Expected key attribute of key1");
        Assertions.assertEquals("value1", item.getTextContent(), "Expected a value of value1");

        item = metaData.item(1);
        Assertions.assertEquals("key2",
                item.getAttributes().getNamedItem("key").getNodeValue(), "Expected key attribute of key2");
        Assertions.assertEquals("value2", item.getTextContent(), "Expected a value of value2");

        item = metaData.item(2);
        Assertions.assertEquals("noValue",
                item.getAttributes().getNamedItem("key").getNodeValue(), "Expected key attribute of noValue");
        Assertions.assertEquals("", item.getTextContent(), "Expected no value");
    }

    private static int getInt(final XMLStreamReader reader) throws XMLStreamException {
        final String value = getString(reader);
        if (value != null) {
            return Integer.parseInt(value);
        }
        return 0;
    }

    private static long getLong(final XMLStreamReader reader) throws XMLStreamException {
        final String value = getString(reader);
        if (value != null) {
            return Long.parseLong(value);
        }
        return 0L;
    }

    private static String getString(final XMLStreamReader reader) throws XMLStreamException {
        final int state = reader.next();
        if (state == XMLStreamConstants.END_ELEMENT) {
            return null;
        }
        if (state == XMLStreamConstants.CHARACTERS) {
            final String text = reader.getText();
            return sanitize(text);
        }
        throw new IllegalStateException("No text");
    }

    private static Map<String, String> getMap(final XMLStreamReader reader) throws XMLStreamException {
        if (reader.hasNext()) {
            int state;
            final Map<String, String> result = new LinkedHashMap<>();
            while (reader.hasNext() && (state = reader.next()) != XMLStreamConstants.END_ELEMENT) {
                if (state == XMLStreamConstants.CHARACTERS) {
                    String text = sanitize(reader.getText());
                    if (text == null || text.isEmpty())
                        continue;
                    Assertions.fail(String.format("Invalid text found: %s", text));
                }
                final String key = reader.getLocalName();
                Assertions.assertTrue(reader.hasNext());
                final String value = getString(reader);
                Assertions.assertNotNull(value);
                result.put(key, value);
            }
            return result;
        }
        return Collections.emptyMap();
    }

    private static String sanitize(final String value) {
        return value == null ? null : value.replaceAll("\n", "").trim();
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
            if (state == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals(Key.EXCEPTION.getKey())) {
                inException = false;
            }
            if (state == XMLStreamConstants.START_ELEMENT) {
                final String localName = reader.getLocalName();
                if (localName.equals(Key.EXCEPTION.getKey())) {
                    inException = true;// TODO (jrp) stack trace may need to be validated
                } else if (localName.equals(Key.LEVEL.getKey())) {
                    Assertions.assertEquals(record.getLevel(), Level.parse(getString(reader)));
                } else if (localName.equals(Key.LOGGER_CLASS_NAME.getKey())) {
                    Assertions.assertEquals(record.getLoggerClassName(), getString(reader));
                } else if (localName.equals(Key.LOGGER_NAME.getKey())) {
                    Assertions.assertEquals(record.getLoggerName(), getString(reader));
                } else if (localName.equals(Key.MDC.getKey())) {
                    compareMap(record.getMdcCopy(), getMap(reader));
                } else if (!inException && localName.equals(Key.MESSAGE.getKey())) {
                    Assertions.assertEquals(record.getFormattedMessage(), getString(reader));
                } else if (localName.equals(Key.NDC.getKey())) {
                    final String value = getString(reader);
                    Assertions.assertEquals(record.getNdc(), (value == null ? "" : value));
                } else if (localName.equals(Key.SEQUENCE.getKey())) {
                    Assertions.assertEquals(record.getSequenceNumber(), getLong(reader));
                } else if (localName.equals(Key.SOURCE_CLASS_NAME.getKey())) {
                    Assertions.assertEquals(record.getSourceClassName(), getString(reader));
                } else if (localName.equals(Key.SOURCE_FILE_NAME.getKey())) {
                    Assertions.assertEquals(record.getSourceFileName(), getString(reader));
                } else if (localName.equals(Key.SOURCE_LINE_NUMBER.getKey())) {
                    Assertions.assertEquals(record.getSourceLineNumber(), getInt(reader));
                } else if (localName.equals(Key.SOURCE_METHOD_NAME.getKey())) {
                    Assertions.assertEquals(record.getSourceMethodName(), getString(reader));
                } else if (localName.equals(Key.THREAD_ID.getKey())) {
                    Assertions.assertEquals(record.getThreadID(), getInt(reader));
                } else if (localName.equals(Key.THREAD_NAME.getKey())) {
                    Assertions.assertEquals(record.getThreadName(), getString(reader));
                } else if (localName.equals(Key.TIMESTAMP.getKey())) {
                    final String dateTime = DATE_TIME_FORMATTER.format(record.getInstant());
                    Assertions.assertEquals(dateTime, getString(reader));
                }
            }
        }
    }

    private static void compareMap(final Map<String, String> m1, final Map<String, String> m2) {
        Assertions.assertEquals(m1.size(), m2.size(), "Map sizes do not match");
        for (String key : m1.keySet()) {
            Assertions.assertTrue(m2.containsKey(key), () -> "Second map does not contain key " + key);
            Assertions.assertEquals(m1.get(key), m2.get(key));
        }
    }

    private static Throwable createMultiNestedCause() {
        final RuntimeException suppressed1 = new RuntimeException("Suppressed 1");
        final IllegalStateException nested1 = new IllegalStateException("Nested 1");
        nested1.addSuppressed(new RuntimeException("Nested 1a"));
        suppressed1.addSuppressed(nested1);
        suppressed1.addSuppressed(new IllegalStateException("Nested 1-2"));

        final RuntimeException suppressed2 = new RuntimeException("Suppressed 2", suppressed1);
        final IllegalStateException nested2 = new IllegalStateException("Nested 2");
        nested2.addSuppressed(new RuntimeException("Nested 2a"));
        suppressed2.addSuppressed(nested2);
        suppressed2.addSuppressed(new IllegalStateException("Nested 2-2"));

        final RuntimeException suppressed3 = new RuntimeException("Suppressed 3");
        final IllegalStateException nested3 = new IllegalStateException("Nested 3");
        nested3.addSuppressed(new RuntimeException("Nested 3a"));
        suppressed3.addSuppressed(nested3);
        suppressed3.addSuppressed(new IllegalStateException("Nested 3-2"));

        final RuntimeException cause = new RuntimeException("This is the cause");
        cause.addSuppressed(suppressed1);
        cause.addSuppressed(suppressed2);
        cause.addSuppressed(suppressed3);
        return cause;
    }
}
