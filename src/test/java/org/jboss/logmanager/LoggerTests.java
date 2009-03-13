/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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

import org.testng.annotations.Test;
import static org.testng.AssertJUnit.*;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

@Test
public final class LoggerTests {
    static {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
    }

    public void testInstall() {
        assertTrue("Wrong logger class", (java.util.logging.Logger.getLogger("test") instanceof Logger));
    }

    public void testHandlerAdd() {
        final NullHandler h1 = new NullHandler();
        final NullHandler h2 = new NullHandler();
        final NullHandler h3 = new NullHandler();
        final Logger logger = Logger.getLogger("testHandlerAdd");
        logger.addHandler(h1);
        logger.addHandler(h2);
        logger.addHandler(h3);
        boolean f1 = false;
        boolean f2 = false;
        boolean f3 = false;
        for (Handler handler : logger.getHandlers()) {
            if (handler == h1) f1 = true;
            if (handler == h2) f2 = true;
            if (handler == h3) f3 = true;
        }
        assertTrue("Handler 1 missing", f1);
        assertTrue("Handler 2 missing", f2);
        assertTrue("Handler 3 missing", f3);
    }

    public void testHandlerRemove() {
        final NullHandler h1 = new NullHandler();
        final NullHandler h2 = new NullHandler();
        final NullHandler h3 = new NullHandler();
        final Logger logger = Logger.getLogger("testHandlerRemove");
        logger.addHandler(h1);
        logger.addHandler(h2);
        logger.addHandler(h3);
        logger.removeHandler(h1);
        boolean f1 = false;
        boolean f2 = false;
        boolean f3 = false;
        for (Handler handler : logger.getHandlers()) {
            if (handler == h1) f1 = true;
            if (handler == h2) f2 = true;
            if (handler == h3) f3 = true;
        }
        assertFalse("Handler 1 wasn't removed", f1);
        assertTrue("Handler 2 missing", f2);
        assertTrue("Handler 3 missing", f3);
    }

    public void testHandlerClear() {
        final NullHandler h1 = new NullHandler();
        final NullHandler h2 = new NullHandler();
        final NullHandler h3 = new NullHandler();
        final Logger logger = Logger.getLogger("testHandlerClear");
        logger.addHandler(h1);
        logger.addHandler(h2);
        logger.addHandler(h3);
        logger.clearHandlers();
        boolean f1 = false;
        boolean f2 = false;
        boolean f3 = false;
        for (Handler handler : logger.getHandlers()) {
            if (handler == h1) f1 = true;
            if (handler == h2) f2 = true;
            if (handler == h3) f3 = true;
        }
        assertFalse("Handler 1 wasn't removed", f1);
        assertFalse("Handler 2 wasn't removed", f2);
        assertFalse("Handler 3 wasn't removed", f3);
    }

    private static final class NullHandler extends Handler {

        public void publish(final LogRecord record) {
        }

        public void flush() {
        }

        public void close() throws SecurityException {
        }
    }
}
