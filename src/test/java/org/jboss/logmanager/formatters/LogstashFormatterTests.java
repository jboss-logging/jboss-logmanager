/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.formatters.StructuredFormatter.Key;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogstashFormatterTests extends AbstractStructuredFormatterTest {
    private static final Map<Key, String> KEY_OVERRIDES = new HashMap<>();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    @Before
    public void before() {
        KEY_OVERRIDES.clear();
    }

    @Test
    public void testLogstashFormat() throws Exception {
        KEY_OVERRIDES.put(Key.TIMESTAMP, "@timestamp");
        final LogstashFormatter formatter = new LogstashFormatter();
        formatter.setPrintDetails(true);
        ExtLogRecord record = createLogRecord("Test formatted %s", "message");
        compareLogstash(record, formatter, 1);

        record = createLogRecord("Test Message");
        formatter.setVersion(2);
        compareLogstash(record, formatter, 2);

        record = createLogRecord(Level.ERROR, "Test formatted %s", "message");
        record.setLoggerName("org.jboss.logmanager.ext.test");
        record.setMillis(System.currentTimeMillis());
        record.setThrown(new RuntimeException("Test Exception"));
        record.putMdc("testMdcKey", "testMdcValue");
        record.setNdc("testNdc");
        compareLogstash(record, formatter, 2);
    }

    @Override
    Class<? extends StructuredFormatter> getFormatterType() {
        return LogstashFormatter.class;
    }

    @Override
    void compare(final Map<String, Consumer<String>> expectedValues, final String formattedMessage) {
        try (JsonReader reader = Json.createReader(new StringReader(formattedMessage))) {
            final JsonObject json = reader.readObject();
            final Iterator<Map.Entry<String, Consumer<String>>> iterator = expectedValues.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<String, Consumer<String>> entry = iterator.next();
                final String key = entry.getKey();
                final JsonValue jsonValue = json.get(key);
                if (jsonValue.getValueType() == ValueType.STRING) {
                    entry.getValue().accept(json.getString(key));
                } else if (jsonValue.getValueType() == ValueType.NUMBER) {
                    entry.getValue().accept(json.getJsonNumber(key).toString());
                } else if (jsonValue.getValueType() == ValueType.NULL) {
                    entry.getValue().accept(null);
                } else {
                    Assert.fail(String.format("Type %s is not implemented: %s", jsonValue.getValueType(), jsonValue));
                }
                iterator.remove();
            }
            Assert.assertTrue("Expected map to be empty " + expectedValues, expectedValues.isEmpty());
        }
    }

    @Override
    Map<String, Consumer<String>> createExpectedValues() {
        final Map<String, Consumer<String>> expectedValues = super.createExpectedValues();
        expectedValues.put("@version", value -> {
            final int version = Integer.parseInt(value);
            Assert.assertEquals(1, version);
        });
        return expectedValues;
    }

    private static int getInt(final JsonObject json, final Key key) {
        final String name = getKey(key);
        if (json.containsKey(name) && !json.isNull(name)) {
            return json.getInt(name);
        }
        return 0;
    }

    private static long getLong(final JsonObject json, final Key key) {
        final String name = getKey(key);
        if (json.containsKey(name) && !json.isNull(name)) {
            return json.getJsonNumber(name).longValue();
        }
        return 0L;
    }

    private static String getString(final JsonObject json, final Key key) {
        final String name = getKey(key);
        if (json.containsKey(name) && !json.isNull(name)) {
            return json.getString(name);
        }
        return null;
    }

    private static Map<String, String> getMap(final JsonObject json, final Key key) {
        final String name = getKey(key);
        if (json.containsKey(name) && !json.isNull(name)) {
            final Map<String, String> result = new LinkedHashMap<>();
            final JsonObject mdcObject = json.getJsonObject(name);
            for (String k : mdcObject.keySet()) {
                final JsonValue value = mdcObject.get(k);
                if (value.getValueType() == ValueType.STRING) {
                    result.put(k, value.toString().replace("\"", ""));
                } else {
                    result.put(k, value.toString());
                }
            }
            return result;
        }
        return Collections.emptyMap();
    }

    private static String getKey(final Key key) {
        if (KEY_OVERRIDES.containsKey(key)) {
            return KEY_OVERRIDES.get(key);
        }
        return key.getKey();
    }

    private static void compareLogstash(final ExtLogRecord record, final ExtFormatter formatter, final int version) {
        compareLogstash(record, formatter.format(record), version);
    }

    private static void compareLogstash(final ExtLogRecord record, final String jsonString, final int version) {
        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            final JsonObject json = reader.readObject();
            compare(record, json);
            final String name = "@version";
            int foundVersion = 0;
            if (json.containsKey(name) && !json.isNull(name)) {
                foundVersion = json.getInt(name);
            }
            Assert.assertEquals(version, foundVersion);
        }
    }

    private static void compare(final ExtLogRecord record, final JsonObject json) {
        Assert.assertEquals(record.getLevel(), Level.parse(getString(json, Key.LEVEL)));
        Assert.assertEquals(record.getLoggerClassName(), getString(json, Key.LOGGER_CLASS_NAME));
        Assert.assertEquals(record.getLoggerName(), getString(json, Key.LOGGER_NAME));
        compareMaps(record.getMdcCopy(), getMap(json, Key.MDC));
        Assert.assertEquals(record.getFormattedMessage(), getString(json, Key.MESSAGE));
        Assert.assertEquals(DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(record.getMillis())),
                getString(json, Key.TIMESTAMP));
        Assert.assertEquals(record.getNdc(), getString(json, Key.NDC));
        // Assert.assertEquals(record.getResourceBundle());
        // Assert.assertEquals(record.getResourceBundleName());
        // Assert.assertEquals(record.getResourceKey());
        Assert.assertEquals(record.getSequenceNumber(), getLong(json, Key.SEQUENCE));
        Assert.assertEquals(record.getSourceClassName(), getString(json, Key.SOURCE_CLASS_NAME));
        Assert.assertEquals(record.getSourceFileName(), getString(json, Key.SOURCE_FILE_NAME));
        Assert.assertEquals(record.getSourceLineNumber(), getInt(json, Key.SOURCE_LINE_NUMBER));
        Assert.assertEquals(record.getSourceMethodName(), getString(json, Key.SOURCE_METHOD_NAME));
        Assert.assertEquals(record.getThreadID(), getInt(json, Key.THREAD_ID));
        Assert.assertEquals(record.getThreadName(), getString(json, Key.THREAD_NAME));
    }
}
