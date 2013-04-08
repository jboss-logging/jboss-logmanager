/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2012, Red Hat, Inc., and individual contributors
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

import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.FileHandler;
import org.junit.Test;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.logging.Formatter;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class FileHandlerPerformanceTests {
    private static final Formatter testFormatter = new PatternFormatter("%m\n");

    private static void initHandler(ExtHandler handler) throws UnsupportedEncodingException {
        handler.setFormatter(testFormatter);
        handler.setLevel(Level.ALL);
        handler.setAutoFlush(true);
        handler.setEncoding("utf-8");
    }

    private static void publish(final ExtHandler handler, final String msg) {
        handler.publish(new ExtLogRecord(Level.INFO, msg, null));
    }

    @Test
    public void testPerformance() throws Exception {
        final FileHandler handler = new FileHandler();
        initHandler(handler);
        final File tempFile = File.createTempFile("jblm-", ".log");
        tempFile.deleteOnExit();
        handler.setFile(tempFile);
        final long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            publish(handler, "Test message " + i);
        }
        // the result is system dependant and can therefore only be checked manually
        // a 'sluggish' build indicates a problem
        System.out.println((System.currentTimeMillis() - start));
    }
}
