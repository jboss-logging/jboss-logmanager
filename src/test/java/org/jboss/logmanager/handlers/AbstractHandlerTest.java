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

package org.jboss.logmanager.handlers;

import java.io.File;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.After;
import org.junit.Before;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AbstractHandlerTest {
    static final File BASE_LOG_DIR;

    static {
        BASE_LOG_DIR = new File(System.getProperty("test.log.dir"));
    }

    final static PatternFormatter FORMATTER = new PatternFormatter("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");

    @Before
    public void setup() throws Exception {
        BASE_LOG_DIR.mkdir();
    }

    @After
    public void cleanUp() throws Exception {
        deleteChildrenRecursively(BASE_LOG_DIR);
    }

    static boolean deleteRecursively(final File dir) {
        if (dir.isDirectory()) {
            final File[] files = dir.listFiles();
            if (files != null) {
                for (final File f : files) {
                    if (f.isDirectory()) {
                        if (!deleteRecursively(f)) {
                            return false;
                        }
                    }
                    if (!f.delete()) {
                        return false;
                    }
                }
            }
        }
        return dir.delete();
    }

    static boolean deleteChildrenRecursively(final File dir) {
        if (dir.isDirectory()) {
            final File[] files = dir.listFiles();
            if (files != null) {
                for (final File f : files) {
                    if (f.isDirectory()) {
                        if (!deleteRecursively(f)) {
                            return false;
                        }
                    }
                    if (!f.delete()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    protected static void configureHandlerDefaults(final ExtHandler handler) {
        handler.setAutoFlush(true);
        handler.setFormatter(FORMATTER);
    }

    protected ExtLogRecord createLogRecord(final String msg) {
        return createLogRecord(org.jboss.logmanager.Level.INFO, msg);
    }

    protected ExtLogRecord createLogRecord(final String format, final Object... args) {
        return createLogRecord(org.jboss.logmanager.Level.INFO, format, args);
    }

    protected ExtLogRecord createLogRecord(final org.jboss.logmanager.Level level, final String msg) {
        return new ExtLogRecord(level, msg, getClass().getName());
    }

    protected ExtLogRecord createLogRecord(final org.jboss.logmanager.Level level, final String format, final Object... args) {
        return new ExtLogRecord(level, String.format(format, args), getClass().getName());
    }
}
