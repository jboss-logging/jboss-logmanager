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

package org.jboss.logmanager.ext.formatters;

import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.ext.formatters.StructuredFormatter.Key;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JsonFormatterTests extends AbstractTest {
    private static final Map<Key, String> KEY_OVERRIDES = new HashMap<>();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    @Before
    public void before() {
        KEY_OVERRIDES.clear();
    }

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
    public void testMetaData() throws Exception {
        final JsonFormatter formatter = new JsonFormatter();
        formatter.setPrintDetails(true);
        formatter.setMetaData("context-id=context1");
        ExtLogRecord record = createLogRecord("Test formatted %s", "message");
        Map<String, String> metaDataMap = MapBuilder.<String, String>create()
                .add("context-id", "context1")
                .build();
        compare(record, formatter, metaDataMap);

        formatter.setMetaData("vendor=Red Hat\\, Inc.,product-type=JBoss");
        metaDataMap = MapBuilder.<String, String>create()
                .add("vendor", "Red Hat, Inc.")
                .add("product-type", "JBoss")
                .build();
        compare(record, formatter, metaDataMap);
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

    private static void compare(final ExtLogRecord record, final ExtFormatter formatter) {
        compare(record, formatter.format(record));
    }

    private static void compare(final ExtLogRecord record, final ExtFormatter formatter, final Map<String, String> metaData) {
        compare(record, formatter.format(record), metaData);
    }

    private static void compare(final ExtLogRecord record, final String jsonString) {
        compare(record, jsonString, null);
    }

    private static void compare(final ExtLogRecord record, final String jsonString, final Map<String, String> metaData) {
        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            final JsonObject json = reader.readObject();
            compare(record, json, metaData);
        }
    }

    private static void compare(final ExtLogRecord record, final JsonObject json, final Map<String, String> metaData) {
        Assert.assertEquals(record.getLevel(), Level.parse(getString(json, Key.LEVEL)));
        Assert.assertEquals(record.getLoggerClassName(), getString(json, Key.LOGGER_CLASS_NAME));
        Assert.assertEquals(record.getLoggerName(), getString(json, Key.LOGGER_NAME));
        compareMaps(record.getMdcCopy(), getMap(json, Key.MDC));
        Assert.assertEquals(record.getFormattedMessage(), getString(json, Key.MESSAGE));
        Assert.assertEquals(DATE_TIME_FORMATTER.format(record.getInstant()),
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
        if (metaData != null) {
            for (String key : metaData.keySet()) {
                Assert.assertEquals(metaData.get(key), json.getString(key));
            }
        }
        final boolean hasFormatted = json.get(getKey(Key.STACK_TRACE)) != null;
        final boolean hasStructured = json.get(getKey(Key.EXCEPTION)) != null;
        validateStackTrace(json, hasFormatted, hasStructured);
    }

    private static void validateStackTrace(final JsonObject json, final boolean validateFormatted, final boolean validateStructured) {
        if (validateFormatted) {
            Assert.assertNotNull(json.get(getKey(Key.STACK_TRACE)));
        }
        if (validateStructured) {
            validateStackTrace(json.getJsonObject(getKey(Key.EXCEPTION)));
        }
    }

    private static void validateStackTrace(final JsonObject json) {
        checkNonNull(json, Key.EXCEPTION_REFERENCE_ID);
        checkNonNull(json, Key.EXCEPTION_TYPE);
        checkNonNull(json, Key.EXCEPTION_MESSAGE);
        checkNonNull(json, Key.EXCEPTION_FRAMES);
        if (json.get(getKey(Key.EXCEPTION_CAUSED_BY)) != null) {
            validateStackTrace(json.getJsonObject(getKey(Key.EXCEPTION_CAUSED_BY)).getJsonObject(getKey(Key.EXCEPTION)));
        }
    }

    private static void checkNonNull(final JsonObject json, final Key key) {
        Assert.assertNotNull(String.format("Missing %s in %s", key, json), json.get(getKey(key)));
    }
}
