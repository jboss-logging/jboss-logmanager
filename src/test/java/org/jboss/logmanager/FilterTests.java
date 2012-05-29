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

import org.jboss.logmanager.ExtLogRecord.FormatStyle;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.testng.annotations.Test;
import static org.testng.AssertJUnit.*;
import org.jboss.logmanager.filters.AcceptAllFilter;
import org.jboss.logmanager.filters.DenyAllFilter;
import org.jboss.logmanager.filters.AllFilter;
import org.jboss.logmanager.filters.AnyFilter;
import org.jboss.logmanager.filters.InvertFilter;
import org.jboss.logmanager.filters.LevelChangingFilter;
import org.jboss.logmanager.filters.LevelFilter;
import org.jboss.logmanager.filters.LevelRangeFilter;
import org.jboss.logmanager.filters.RegexFilter;
import org.jboss.logmanager.filters.SubstituteFilter;

import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Test
public final class FilterTests {
    static {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
    }

    private static final Filter[] NO_FILTERS = new Filter[0];

    public void testAcceptAllFilter() {
        final Filter filter = AcceptAllFilter.getInstance();
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testDenyAllFilter() {
        final Filter filter = DenyAllFilter.getInstance();
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testAllFilter0() {
        final Filter filter = new AllFilter(NO_FILTERS);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testAllFilter1() {
        final Filter filter = new AllFilter(new Filter[] {
                AcceptAllFilter.getInstance(),
                AcceptAllFilter.getInstance(),
                AcceptAllFilter.getInstance(),
        });
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testAllFilter2() {
        final Filter filter = new AllFilter(new Filter[] {
                AcceptAllFilter.getInstance(),
                DenyAllFilter.getInstance(),
                AcceptAllFilter.getInstance(),
        });
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testAllFilter3() {
        final Filter filter = new AllFilter(new Filter[] {
                DenyAllFilter.getInstance(),
                DenyAllFilter.getInstance(),
                DenyAllFilter.getInstance(),
        });
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testAnyFilter0() {
        final Filter filter = new AnyFilter(NO_FILTERS);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testAnyFilter1() {
        final Filter filter = new AnyFilter(new Filter[] {
                AcceptAllFilter.getInstance(),
                AcceptAllFilter.getInstance(),
                AcceptAllFilter.getInstance(),
        });
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testAnyFilter2() {
        final Filter filter = new AnyFilter(new Filter[] {
                AcceptAllFilter.getInstance(),
                DenyAllFilter.getInstance(),
                AcceptAllFilter.getInstance(),
        });
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testAnyFilter3() {
        final Filter filter = new AnyFilter(new Filter[] {
                DenyAllFilter.getInstance(),
                DenyAllFilter.getInstance(),
                DenyAllFilter.getInstance(),
        });
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testInvertFilter0() {
        final Filter filter = new InvertFilter(AcceptAllFilter.getInstance());
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testInvertFilter1() {
        final Filter filter = new InvertFilter(DenyAllFilter.getInstance());
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testLevelChangingFilter0() {
        final Filter filter = new LevelChangingFilter(Level.INFO);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.FINEST);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.finest("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testLevelFilter0() {
        final Filter filter = new LevelFilter(Level.INFO);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testLevelFilter1() {
        final Filter filter = new LevelFilter(Level.WARNING);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testLevelRangeFilter0() {
        final Filter filter = new LevelRangeFilter(Level.DEBUG, true, Level.WARN, true);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testLevelRangeFilter1() {
        final Filter filter = new LevelRangeFilter(Level.DEBUG, true, Level.WARN, true);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.severe("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testLevelRangeFilter2() {
        final Filter filter = new LevelRangeFilter(Level.DEBUG, true, Level.WARN, true);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.DEBUG);
        logger.setFilter(filter);
        handler.setLevel(Level.DEBUG);
        logger.log(Level.DEBUG, "This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testLevelRangeFilter3() {
        final Filter filter = new LevelRangeFilter(Level.DEBUG, false, Level.WARN, true);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.DEBUG);
        logger.setFilter(filter);
        handler.setLevel(Level.DEBUG);
        logger.log(Level.DEBUG, "This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testLevelRangeFilter4() {
        final Filter filter = new LevelRangeFilter(Level.DEBUG, true, Level.WARN, false);
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.warning("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testRegexFilter0() {
        final Filter filter = new RegexFilter("test");
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertTrue("Handler wasn't run", ran.get());
    }

    public void testRegexFilter1() {
        final Filter filter = new RegexFilter("pest");
        final AtomicBoolean ran = new AtomicBoolean();
        final Handler handler = new CheckingHandler(ran);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test.");
        assertFalse("Handler was run", ran.get());
    }

    public void testSubstitueFilter0() {
        final Filter filter = new SubstituteFilter(Pattern.compile("test"), "lunch", true);
        final AtomicReference<String> result = new AtomicReference<String>();
        final Handler handler = new MessageCheckingHandler(result);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test test.");
        assertEquals("Substitution was not correctly applied", "This is a lunch lunch.", result.get());
    }

    public void testSubstituteFilter1() {
        final Filter filter = new SubstituteFilter(Pattern.compile("test"), "lunch", false);
        final AtomicReference<String> result = new AtomicReference<String>();
        final Handler handler = new MessageCheckingHandler(result);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test test.");
        assertEquals("Substitution was not correctly applied", "This is a lunch test.", result.get());
    }

    public void testSubstituteFilter2() {
        final Filter filter = new SubstituteFilter(Pattern.compile("t(es)t"), "lunch$1", true);
        final AtomicReference<String> result = new AtomicReference<String>();
        final Handler handler = new MessageCheckingHandler(result);
        final Logger logger = Logger.getLogger("filterTest");
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setFilter(filter);
        handler.setLevel(Level.INFO);
        logger.info("This is a test test.");
        assertEquals("Substitution was not correctly applied", "This is a lunches lunches.", result.get());
    }

    public void testSubstituteFilter3() {
        final Filter filter = new SubstituteFilter(Pattern.compile("t(es)t"), "lunch$1", true);
        final ExtLogRecord record = new ExtLogRecord(Level.INFO, "This is a test %s", FormatStyle.PRINTF, FilterTests.class.getName());
        record.setParameters(new String[] {"test"});
        filter.isLoggable(record);
        assertEquals("Substitution was not correctly applied", "This is a lunches test", record.getFormattedMessage());
    }



    private static final class MessageCheckingHandler extends Handler {
        private final AtomicReference<String> msg;

        private MessageCheckingHandler(final AtomicReference<String> msg) {
            this.msg = msg;
        }

        public void publish(final LogRecord record) {
            msg.set(record.getMessage());
        }

        public void flush() {
        }

        public void close() throws SecurityException {
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
}
