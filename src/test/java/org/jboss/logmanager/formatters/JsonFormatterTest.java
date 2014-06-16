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
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtLogRecord.FormatStyle;
import org.jboss.logmanager.Level;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JsonFormatterTest extends AbstractTest {

    @Test
    public void testFormat() throws Exception {
        final JsonFormatter formatter = new JsonFormatter();
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

    private static int getInt(final JsonObject json, final String name) {
        if (json.containsKey(name) && !json.isNull(name)) {
            return json.getInt(name);
        }
        return 0;
    }

    private static long getLong(final JsonObject json, final String name) {
        if (json.containsKey(name) && !json.isNull(name)) {
            return json.getJsonNumber(name).longValue();
        }
        return 0L;
    }

    private static String getString(final JsonObject json, final String name) {
        if (json.containsKey(name) && !json.isNull(name)) {
            return json.getString(name);
        }
        return null;
    }

    private static List<String> getArray(final JsonObject json, final String name) {
        if (json.containsKey(name) && !json.isNull(name)) {
            final List<String> result = new ArrayList<>();
            for (JsonValue value : json.getJsonArray(name)) {
                if (value.getValueType() == ValueType.STRING) {
                    result.add(value.toString().replace("\"", ""));
                } else {
                    result.add(value.toString());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static Map<String, String> getMap(final JsonObject json, final String name) {
        if (json.containsKey(name) && !json.isNull(name)) {
            final Map<String, String> result = new LinkedHashMap<>();
            final JsonObject mdcObject = json.getJsonObject(name);
            for (String key : mdcObject.keySet()) {
                final JsonValue value = mdcObject.get(key);
                if (value.getValueType() == ValueType.STRING) {
                    result.put(key, value.toString().replace("\"", ""));
                } else {
                    result.put(key, value.toString());
                }
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

    private static void compare(final ExtLogRecord record, final ExtFormatter formatter) {
        compare(record, formatter.format(record));
    }

    private static void compare(final ExtLogRecord record, final String jsonString) {
        final JsonReader reader = Json.createReader(new StringReader(jsonString));
        final JsonObject json = reader.readObject();

        Assert.assertEquals(record.getFormatStyle(), FormatStyle.valueOf(getString(json, StructuredFormatter.Keys.FORMAT_STYLE_KEY)));
        Assert.assertEquals(record.getFormattedMessage(), getString(json, StructuredFormatter.Keys.FORMATTED_MESSAGE_KEY));
        Assert.assertEquals(record.getLevel(), Level.parse(getString(json, StructuredFormatter.Keys.LEVEL_KEY)));
        Assert.assertEquals(record.getLoggerClassName(), getString(json, StructuredFormatter.Keys.LOGGER_CLASS_NAME_KEY));
        Assert.assertEquals(record.getLoggerName(), getString(json, StructuredFormatter.Keys.LOGGER_NAME_KEY));
        compareMap(record.getMdcCopy(), getMap(json, StructuredFormatter.Keys.MDC_KEY));
        Assert.assertEquals(record.getMessage(), getString(json, StructuredFormatter.Keys.MESSAGE_KEY));
        Assert.assertEquals(
                new SimpleDateFormat(StructuredFormatter.DEFAULT_DATE_FORMAT).format(new Date(record.getMillis())),
                getString(json, StructuredFormatter.Keys.TIMESTAMP_KEY));
        Assert.assertEquals(record.getNdc(), getString(json, StructuredFormatter.Keys.NDC_KEY));
        Assert.assertTrue(String.format("%n%s%n%s", convert(record.getParameters()), getArray(json, StructuredFormatter.Keys.PARAMETERS_KEY)),
                convert(record.getParameters()).containsAll(getArray(json, StructuredFormatter.Keys.PARAMETERS_KEY)));
        // Assert.assertEquals(record.getResourceBundle());
        // Assert.assertEquals(record.getResourceBundleName());
        // Assert.assertEquals(record.getResourceKey());
        Assert.assertEquals(record.getSequenceNumber(), getLong(json, StructuredFormatter.Keys.SEQUENCE_KEY));
        Assert.assertEquals(record.getSourceClassName(), getString(json, StructuredFormatter.Keys.SOURCE_CLASS_NAME_KEY));
        Assert.assertEquals(record.getSourceFileName(), getString(json, StructuredFormatter.Keys.SOURCE_FILE_NAME_KEY));
        Assert.assertEquals(record.getSourceLineNumber(), getInt(json, StructuredFormatter.Keys.SOURCE_LINE_NUMBER_KEY));
        Assert.assertEquals(record.getSourceMethodName(), getString(json, StructuredFormatter.Keys.SOURCE_METHOD_NAME_KEY));
        Assert.assertEquals(record.getThreadID(), getInt(json, StructuredFormatter.Keys.THREAD_ID_KEY));
        Assert.assertEquals(record.getThreadName(), getString(json, StructuredFormatter.Keys.THREAD_NAME_KEY));
        // TODO (jrp) stack trace should be validated
    }

    private static void compareMap(final Map<String, String> m1, final Map<String, String> m2) {
        Assert.assertEquals("Map sizes do not match", m1.size(), m2.size());
        for (String key : m1.keySet()) {
            Assert.assertTrue("Second map does not contain key " + key, m2.containsKey(key));
            Assert.assertEquals(m1.get(key), m2.get(key));
        }
    }
}
