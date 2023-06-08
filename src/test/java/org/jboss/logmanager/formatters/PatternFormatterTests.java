/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.MDC;
import org.jboss.logmanager.NDC;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PatternFormatterTests {
    static final String CATEGORY = "org.jboss.logmanager.formatters.PatternFormatterTests";

    static {
        // Set a system property
        System.setProperty("org.jboss.logmanager.testProp", "testValue");
    }

    @Test
    public void categories() throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        PatternFormatter formatter = new PatternFormatter("%c");
        Assertions.assertEquals(CATEGORY, formatter.format(record));

        formatter = new PatternFormatter("%c{1}");
        Assertions.assertEquals("PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{2}");
        Assertions.assertEquals("formatters.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{1.}");
        Assertions.assertEquals("o.j.l.f.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{1.~}");
        Assertions.assertEquals("o.~.~.~.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{.}");
        Assertions.assertEquals("....PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{1~.}");
        Assertions.assertEquals("o~.j~.l~.f~.PatternFormatterTests", formatter.format(record));

        // Test a simple logger name
        record.setLoggerName("test");
        formatter = new PatternFormatter("%c{1}");
        Assertions.assertEquals("test", formatter.format(record));

        formatter = new PatternFormatter("%c{.}");
        Assertions.assertEquals("test", formatter.format(record));

        formatter = new PatternFormatter("%c{1~.}");
        Assertions.assertEquals("test", formatter.format(record));
    }

    @Test
    public void classNames() throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        PatternFormatter formatter = new PatternFormatter("%C");
        Assertions.assertEquals(PatternFormatterTests.class.getName(), formatter.format(record));

        formatter = new PatternFormatter("%C{1}");
        Assertions.assertEquals("PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{2}");
        Assertions.assertEquals("formatters.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{1.}");
        Assertions.assertEquals("o.j.l.f.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{1.~}");
        Assertions.assertEquals("o.~.~.~.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{.}");
        Assertions.assertEquals("....PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{1~.}");
        Assertions.assertEquals("o~.j~.l~.f~.PatternFormatterTests", formatter.format(record));
    }

    @Test
    public void ndc() throws Exception {
        NDC.push("value1");
        NDC.push("value2");
        NDC.push("value3");
        final ExtLogRecord record = createLogRecord("test");

        PatternFormatter formatter = new PatternFormatter("%x");
        Assertions.assertEquals("value1.value2.value3", formatter.format(record));

        formatter = new PatternFormatter("%x{1}");
        Assertions.assertEquals("value3", formatter.format(record));

        formatter = new PatternFormatter("%x{2}");
        Assertions.assertEquals("value2.value3", formatter.format(record));
    }

    @Test
    public void mdc() throws Exception {
        try {
            MDC.put("primaryKey", "primaryValue");
            MDC.put("key1", "value1");
            MDC.put("key2", "value2");
            final ExtLogRecord record = createLogRecord("test");

            PatternFormatter formatter = new PatternFormatter("%X{key1}");
            Assertions.assertEquals("value1", formatter.format(record));

            formatter = new PatternFormatter("%X{not.found}");
            Assertions.assertEquals("", formatter.format(record));

            formatter = new PatternFormatter("%X");
            String formatted = formatter.format(record);
            Assertions.assertEquals("{key1=value1, key2=value2, primaryKey=primaryValue}", formatted);
        } finally {
            MDC.clear();
        }
    }

    @Test
    public void threads() throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        record.setThreadName("testThreadName");
        record.setThreadID(33);
        PatternFormatter formatter = new PatternFormatter("%t");
        Assertions.assertEquals("testThreadName", formatter.format(record));

        formatter = new PatternFormatter("%t{id}");
        Assertions.assertEquals("33", formatter.format(record));

        formatter = new PatternFormatter("%t{ID}");
        Assertions.assertEquals("33", formatter.format(record));
    }

    @Test
    public void systemProperties() throws Exception {
        systemProperties("$");
        systemProperties("#");
    }

    @Test
    public void truncation() throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        PatternFormatter formatter = new PatternFormatter("'%-10.-21c' %m");
        Assertions.assertEquals("'PatternFormatterTests' test", formatter.format(record));

        formatter = new PatternFormatter("'%10.-21c' %m");
        Assertions.assertEquals("'PatternFormatterTests' test", formatter.format(record));

        formatter = new PatternFormatter("%.-21C %m");
        Assertions.assertEquals("PatternFormatterTests test", formatter.format(record));

        formatter = new PatternFormatter("%.2m");
        Assertions.assertEquals("te", formatter.format(record));

        formatter = new PatternFormatter("%.-2m");
        Assertions.assertEquals("st", formatter.format(record));

        formatter = new PatternFormatter("%5m");
        Assertions.assertEquals(" test", formatter.format(record));

        formatter = new PatternFormatter("%-5.-10m");
        Assertions.assertEquals("test ", formatter.format(record));

        formatter = new PatternFormatter("%-5.10m");
        Assertions.assertEquals("test ", formatter.format(record));

        // Exact length truncation
        final String msg = "test message";
        formatter = new PatternFormatter("%c %-5.-7m");
        Assertions.assertEquals(CATEGORY + " message", formatter.format(createLogRecord(msg)));
    }

    @Test
    public void extendedThrowable() throws Exception {
        ExtLogRecord record = createLogRecord("test");

        Throwable cause = new IllegalArgumentException("cause");
        Throwable level1 = new RuntimeException("level1", cause);
        Throwable suppressedLevel1 = new IllegalStateException("suppressedLevel1");
        Throwable suppressedLevel1a = new RuntimeException("suppressedLevel1a");
        Throwable suppressedLevel2 = new IllegalThreadStateException("suppressedLevel2");
        suppressedLevel1.addSuppressed(suppressedLevel2);

        level1.addSuppressed(suppressedLevel1);
        level1.addSuppressed(suppressedLevel1a);

        record.setThrown(level1);

        // All exceptions
        PatternFormatter formatter = new PatternFormatter("%e");

        String formatted = formatter.format(record);

        // Should contain, level1, cause, level1a, suppressedLevel1 and suppressedLevel2
        Assertions.assertTrue(formatted.contains("cause"));
        Assertions.assertTrue(formatted.contains("level1"));
        Assertions.assertTrue(formatted.contains("suppressedLevel1"));
        Assertions.assertTrue(formatted.contains("suppressedLevel1a"));
        Assertions.assertTrue(formatted.contains("suppressedLevel2"));

        // No suppressed exceptions
        formatter = new PatternFormatter("%e{0}");
        formatted = formatter.format(record);

        // Should only contain cause and level1
        Assertions.assertTrue(formatted.contains("cause"));
        Assertions.assertTrue(formatted.contains("level1"));
        Assertions.assertFalse(formatted.contains("suppressedLevel1"));
        Assertions.assertFalse(formatted.contains("suppressedLevel1a"));
        Assertions.assertFalse(formatted.contains("suppressedLevel2"));

        // One level suppressed exceptions
        formatter = new PatternFormatter("%E{1}");
        formatted = formatter.format(record);

        // Should only contain cause and level1
        Assertions.assertTrue(formatted.contains("cause"));
        Assertions.assertTrue(formatted.contains("level1"));
        Assertions.assertTrue(formatted.contains("suppressedLevel1"));
        Assertions.assertFalse(formatted.contains("suppressedLevel1a"));
        Assertions.assertFalse(formatted.contains("suppressedLevel2"));

        // Add a circular reference to the cause. This should test both that the caused suppressed exceptions are being
        // printed and that circular exceptions aren't being processed again.
        formatter = new PatternFormatter("%e");
        cause.addSuppressed(suppressedLevel1);
        formatted = formatter.format(record);
        Assertions.assertTrue(formatted.contains("CIRCULAR REFERENCE: java.lang.IllegalStateException: suppressedLevel1"));
    }

    @Test
    public void unqualifiedHost() {
        final String hostName = "logmanager.jboss.org";
        final ExtLogRecord record = createLogRecord("test");
        record.setHostName(hostName);
        PatternFormatter formatter = new PatternFormatter("%h");
        Assertions.assertEquals("logmanager", formatter.format(record));

        // This should still return just the first portion
        formatter = new PatternFormatter("%h{2}");
        Assertions.assertEquals("logmanager", formatter.format(record));

        // Should truncate from the beginning
        formatter = new PatternFormatter("%.3h");
        Assertions.assertEquals("log", formatter.format(record));

        // Should truncate from the end
        formatter = new PatternFormatter("%.-7h");
        Assertions.assertEquals("manager", formatter.format(record));
    }

    @Test
    public void qualifiedHost() {
        final String hostName = "logmanager.jboss.org";
        final ExtLogRecord record = createLogRecord("test");
        record.setHostName(hostName);
        PatternFormatter formatter = new PatternFormatter("%H");
        Assertions.assertEquals(hostName, formatter.format(record));

        formatter = new PatternFormatter("%H{1}");
        Assertions.assertEquals("logmanager", formatter.format(record));

        formatter = new PatternFormatter("%H{2}");
        Assertions.assertEquals("logmanager.jboss", formatter.format(record));

        formatter = new PatternFormatter("%H{3}");
        Assertions.assertEquals(hostName, formatter.format(record));

        formatter = new PatternFormatter("%H{4}");
        Assertions.assertEquals(hostName, formatter.format(record));

        // Truncate from the beginning
        formatter = new PatternFormatter("%.10H");
        Assertions.assertEquals("logmanager", formatter.format(record));

        // Truncate from the end
        formatter = new PatternFormatter("%.-3H");
        Assertions.assertEquals("org", formatter.format(record));
        formatter = new PatternFormatter("%.-5H{2}");
        Assertions.assertEquals("jboss", formatter.format(record));
    }

    private void systemProperties(final String propertyPrefix) throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        PatternFormatter formatter = new PatternFormatter("%" + propertyPrefix + "{org.jboss.logmanager.testProp}");
        Assertions.assertEquals("testValue", formatter.format(record));

        formatter = new PatternFormatter("%" + propertyPrefix + "{invalid:defaultValue}");
        Assertions.assertEquals("defaultValue", formatter.format(record));

        formatter = new PatternFormatter("%" + propertyPrefix + "{invalid}");
        Assertions.assertEquals("null", formatter.format(record));

        // Test null arguments
        try {
            formatter = new PatternFormatter("%" + propertyPrefix);
            formatter.format(record);
            Assertions.fail("Should not allow null arguments");
        } catch (IllegalArgumentException ignore) {

        }

        try {
            formatter = new PatternFormatter("%" + propertyPrefix + "{}");
            formatter.format(record);
            Assertions.fail("Should not allow null arguments");
        } catch (IllegalArgumentException ignore) {

        }
    }

    protected static ExtLogRecord createLogRecord(final String msg) {
        final ExtLogRecord result = new ExtLogRecord(org.jboss.logmanager.Level.INFO, msg,
                PatternFormatterTests.class.getName());
        result.setSourceClassName(PatternFormatterTests.class.getName());
        result.setLoggerName(CATEGORY);
        return result;
    }
}
