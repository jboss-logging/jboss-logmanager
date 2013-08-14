/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
