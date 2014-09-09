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
import org.jboss.logmanager.NDC;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertEquals(CATEGORY, formatter.format(record));

        formatter = new PatternFormatter("%c{1}");
        Assert.assertEquals("PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{2}");
        Assert.assertEquals("formatters.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{1.}");
        Assert.assertEquals("o.j.l.f.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{1.~}");
        Assert.assertEquals("o.~.~.~.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{.}");
        Assert.assertEquals("....PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%c{1~.}");
        Assert.assertEquals("o~.j~.l~.f~.PatternFormatterTests", formatter.format(record));

        // Test a simple logger name
        record.setLoggerName("test");
        formatter = new PatternFormatter("%c{1}");
        Assert.assertEquals("test", formatter.format(record));

        formatter = new PatternFormatter("%c{.}");
        Assert.assertEquals("test", formatter.format(record));

        formatter = new PatternFormatter("%c{1~.}");
        Assert.assertEquals("test", formatter.format(record));
    }

    @Test
    public void classNames() throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        PatternFormatter formatter = new PatternFormatter("%C");
        Assert.assertEquals(PatternFormatterTests.class.getName(), formatter.format(record));

        formatter = new PatternFormatter("%C{1}");
        Assert.assertEquals("PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{2}");
        Assert.assertEquals("formatters.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{1.}");
        Assert.assertEquals("o.j.l.f.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{1.~}");
        Assert.assertEquals("o.~.~.~.PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{.}");
        Assert.assertEquals("....PatternFormatterTests", formatter.format(record));

        formatter = new PatternFormatter("%C{1~.}");
        Assert.assertEquals("o~.j~.l~.f~.PatternFormatterTests", formatter.format(record));
    }

    @Test
    public void ndc() throws Exception {
        NDC.push("value1");
        NDC.push("value2");
        NDC.push("value3");
        final ExtLogRecord record = createLogRecord("test");

        PatternFormatter formatter = new PatternFormatter("%x");
        Assert.assertEquals("value1.value2.value3", formatter.format(record));

        formatter = new PatternFormatter("%x{1}");
        Assert.assertEquals("value3", formatter.format(record));

        formatter = new PatternFormatter("%x{2}");
        Assert.assertEquals("value2.value3", formatter.format(record));
    }

    @Test
    public void threads() throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        record.setThreadName("testThreadName");
        record.setThreadID(33);
        PatternFormatter formatter = new PatternFormatter("%t");
        Assert.assertEquals("testThreadName", formatter.format(record));

        formatter = new PatternFormatter("%t{id}");
        Assert.assertEquals("33", formatter.format(record));

        formatter = new PatternFormatter("%t{ID}");
        Assert.assertEquals("33", formatter.format(record));
    }

    @Test
    public void systemProperties() throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        PatternFormatter formatter = new PatternFormatter("%${org.jboss.logmanager.testProp}");
        Assert.assertEquals("testValue", formatter.format(record));

        formatter = new PatternFormatter("%${invalid:defaultValue}");
        Assert.assertEquals("defaultValue", formatter.format(record));

        formatter = new PatternFormatter("%${invalid}");
        Assert.assertEquals("null", formatter.format(record));

        // Test null arguments
        try {
            formatter = new PatternFormatter("%$");
            formatter.format(record);
            Assert.fail("Should not allow null arguments");
        } catch (IllegalArgumentException ignore) {

        }

        try {
            formatter = new PatternFormatter("%${}");
            formatter.format(record);
            Assert.fail("Should not allow null arguments");
        } catch (IllegalArgumentException ignore) {

        }
    }

    @Test
    public void truncation() throws Exception {
        final ExtLogRecord record = createLogRecord("test");
        PatternFormatter formatter = new PatternFormatter("'%-10.-21c' %m");
        Assert.assertEquals("'PatternFormatterTests' test", formatter.format(record));

        formatter = new PatternFormatter("'%10.-21c' %m");
        Assert.assertEquals("'PatternFormatterTests' test", formatter.format(record));

        formatter = new PatternFormatter("%.-21C %m");
        Assert.assertEquals("PatternFormatterTests test", formatter.format(record));

        formatter = new PatternFormatter("%.2m");
        Assert.assertEquals("te", formatter.format(record));

        formatter = new PatternFormatter("%.-2m");
        Assert.assertEquals("st", formatter.format(record));

        formatter = new PatternFormatter("%5m");
        Assert.assertEquals(" test", formatter.format(record));

        formatter = new PatternFormatter("%-5.-10m");
        Assert.assertEquals("test ", formatter.format(record));

        formatter = new PatternFormatter("%-5.10m");
        Assert.assertEquals("test ", formatter.format(record));
    }

    protected static ExtLogRecord createLogRecord(final String msg) {
        final ExtLogRecord result = new ExtLogRecord(org.jboss.logmanager.Level.INFO, msg, PatternFormatterTests.class.getName());
        result.setSourceClassName(PatternFormatterTests.class.getName());
        result.setLoggerName(CATEGORY);
        return result;
    }
}
