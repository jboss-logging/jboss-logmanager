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

package org.jboss.logmanager;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogManagerTests extends AbstractTest {
    static {
        // Access a logger in initialize the logmanager
        java.util.logging.Logger.getAnonymousLogger();
    }

    private final java.util.logging.Level[] levels = {
            Level.TRACE,
            Level.DEBUG,
            Level.INFO,
            Level.WARN,
            Level.ERROR,
            Level.FATAL,
            java.util.logging.Level.ALL,
            java.util.logging.Level.FINEST,
            java.util.logging.Level.FINER,
            java.util.logging.Level.FINE,
            java.util.logging.Level.INFO,
            java.util.logging.Level.CONFIG,
            java.util.logging.Level.WARNING,
            java.util.logging.Level.SEVERE,
            java.util.logging.Level.OFF,
    };

    @Test
    public void testLevelReplacement() throws Exception {
        // Validate each level
        for (java.util.logging.Level l : levels) {
            java.util.logging.Level level = java.util.logging.Level.parse(l.getName());
            Assert.assertEquals(l, level);
            level = java.util.logging.Level.parse(Integer.toString(l.intValue()));
            Assert.assertEquals(l, level);
        }
    }

    @Test
    public void checkLoggerNames() {
        // Get the log manager
        final java.util.logging.LogManager logManager = java.util.logging.LogManager.getLogManager();
        // Should be a org.jboss.logmanager.LogManager
        Assert.assertEquals(LogManager.class, logManager.getClass());

        final List<String> expectedNames = Arrays.asList("", "test1", "org.jboss", "org.jboss.logmanager", "other", "stdout");

        // Create the loggers
        for (String name : expectedNames) {
            Logger.getLogger(name);
        }
        compare(expectedNames, Collections.list(logManager.getLoggerNames()));
    }

    private <T> void compare(final Collection<T> expected, final Collection<T> actual) {
        Assert.assertTrue("Expected: " + expected.toString() + " Actual: " + actual.toString(), expected.containsAll(actual));
        Assert.assertTrue("Expected: " + expected.toString() + " Actual: " + actual.toString(), actual.containsAll(expected));
    }
}
