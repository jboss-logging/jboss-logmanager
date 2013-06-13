/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
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
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LoggerTests {

    @Test
    public void testInstall() {
        assertTrue("Wrong logger class", (java.util.logging.Logger.getLogger("test") instanceof Logger));
    }

    @Test
    public void testCategories() {
        assertNotNull("Logger not created with category: " + LoggerTests.class.getName(), Logger.getLogger(LoggerTests.class.getName()));
        assertNotNull("Logger not created with category: Spaced Logger Name", Logger.getLogger("Spaced Logger Name"));
        assertNotNull("Logger not created with category: /../Weird/Path", Logger.getLogger("/../Weird/Path"));
        assertNotNull("Logger not created with category: random.chars.`~!@#$%^&*()-=_+[]{}\\|;':\",.<>/?", Logger.getLogger("random.chars.`~!@#$%^&*()-=_+[]{}\\|;':\",.<>/?"));
    }

    @Test
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

    @Test
    public void testHandlerAdd2() {
        final NullHandler h1 = new NullHandler();
        final Logger logger = Logger.getLogger("testHandlerAdd2");
        logger.addHandler(h1);
        logger.addHandler(h1);
        logger.addHandler(h1);
        boolean f1 = false;
        final Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            if (handler == h1) f1 = true;
        }
        assertTrue("Handler 1 missing", f1);
        assertEquals("Extra handlers missing", 3, handlers.length);
    }

    @Test
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

    @Test
    public void testHandlerRemove2() {
        final NullHandler h1 = new NullHandler();
        final Logger logger = Logger.getLogger("testHandlerRemove2");
        logger.removeHandler(h1);
        final Handler[] handlers = logger.getHandlers();
        assertEquals(0, handlers.length);
    }

    @Test
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

    @Test
    public void testHandlerRun() {
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("testHandlerRun");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    @Test
    public void testResourceBundle() {
        final ListHandler handler = new ListHandler();
        final Logger logger = Logger.getLogger("rbLogger", getClass().getName());
        logger.setLevel(Level.INFO);
        handler.setLevel(Level.INFO);
        logger.addHandler(handler);
        logger.log(Level.INFO, null, new IllegalArgumentException());
        logger.log(Level.INFO, "test", new IllegalArgumentException());
        assertEquals(null, handler.messages.get(0));
        assertEquals("Test message", handler.messages.get(1));
    }

    private static final class ListHandler extends ExtHandler {
        final List<String> messages = Collections.synchronizedList(new ArrayList<String>());

        ListHandler() {
            super();
            setFormatter(new PatternFormatter("%s"));
        }

        @Override
        protected void doPublish(final ExtLogRecord record) {
            super.doPublish(record);
            messages.add(record.getFormattedMessage());
        }
    }

    private static final class CheckingHandler extends Handler {
        private final AtomicBoolean ran;

        public CheckingHandler(final AtomicBoolean ran) {
            this.ran = ran;
        }

        public void publish(final LogRecord record) {
            if (isLoggable(record)) {
                ran.set(true);
            }
        }

        public void flush() {
        }

        public void close() throws SecurityException {
        }
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
