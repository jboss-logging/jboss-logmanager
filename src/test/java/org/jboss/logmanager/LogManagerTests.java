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
        // Get the static method to find the level by name
        Method method = null;
        try {
            method = java.util.logging.Level.class.getDeclaredMethod("findLevel", String.class);
            method.setAccessible(true);
        } catch (NoSuchMethodException ignore) {
        }
        // Validate each level
        for (java.util.logging.Level l : levels) {
            java.util.logging.Level level = java.util.logging.Level.parse(l.getName());
            Assert.assertEquals(l, level);
            if (method != null) {
                // Hack to check the level by int
                level = (java.util.logging.Level) method.invoke(null, Integer.toString(level.intValue()));
                Assert.assertEquals(l, level);
            }
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
