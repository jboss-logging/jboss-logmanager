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

import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class ForceLogTests extends AbstractTest {

    private static final String FILTERED = "FILTERED";
    private static final List<String> LOG_MESSAGES;

    static {
        LOG_MESSAGES = Collections.unmodifiableList(Arrays.asList(
                "FILTERED",
                "a",
                "ENTRY",
                "ENTRY {0}",
                "ENTRY {0} {1}",
                "RETURN",
                "RETURN {0}",
                "THROW",
                "h",
                "i",
                "j",
                "k",
                "l",
                "m",
                "n",
                "o",
                "p {0}",
                "q {0} {1}",
                "r",
                "s",
                "t {0}",
                "u {0} {1}",
                "v",
                "x",
                "y {0}",
                "z {0} {1}",
                "a1",
                "b1 {0}",
                "c1 {0}",
                "d1"
        ));
    }

    // it calls all the logging methods.
    private void callLog(Logger logger) {
        logger.log(Level.SEVERE, FILTERED);
        logger.log(new LogRecord(Level.SEVERE, "a"));
        logger.entering(ForceLogTests.class.getName(), "b");
        logger.entering(ForceLogTests.class.getName(), "c {0}", "C");
        logger.entering(ForceLogTests.class.getName(), "d {0} {1}", new Object[]{"D", "DD"});
        logger.exiting(ForceLogTests.class.getName(), "e");
        logger.exiting(ForceLogTests.class.getName(), "f", "F");
        logger.throwing(ForceLogTests.class.getName(), "g", new Exception("G"));
        logger.severe("h");
        logger.warning("i");
        logger.info("j");
        logger.config("k");
        logger.fine("l");
        logger.finer("m");
        logger.finest("n");
        logger.log(Level.SEVERE, "o");
        logger.log(Level.SEVERE, "p {0}", "P");
        logger.log(Level.SEVERE, "q {0} {1}", new Object[]{"Q", "QQ"});
        logger.log(Level.SEVERE, "r", new Exception("R"));
        logger.logp(Level.SEVERE, ForceLogTests.class.getName(), "sourceMethod_s", "s");
        logger.logp(Level.SEVERE, ForceLogTests.class.getName(), "sourceMethod_t", "t {0}", "T");
        logger.logp(Level.SEVERE, ForceLogTests.class.getName(), "sourceMethod_u", "u {0} {1}", new Object[]{"U", "UU"});
        logger.logp(Level.SEVERE, ForceLogTests.class.getName(), "sourceMethod_v", "v", new Exception("v"));
        logger.logrb(Level.SEVERE, ForceLogTests.class.getName(), "sourceMethod_x", "bundleName_x", "x");
        logger.logrb(Level.SEVERE, ForceLogTests.class.getName(), "sourceMethod_y", "bundleName_y", "y {0}", "Y");
        logger.logrb(Level.SEVERE, ForceLogTests.class.getName(), "sourceMethod_z", "bundleName_z", "z {0} {1}", new Object[]{"Z", "ZZ"});
        logger.logrb(Level.SEVERE, ForceLogTests.class.getName(), "sourceMethod__a1", "bundleName_a1", "a1", new Exception("a1"));
        logger.log("fqcn_b1", Level.SEVERE, "b1 {0}", "bundleName_b1", ExtLogRecord.FormatStyle.MESSAGE_FORMAT, new Object[]{"B1"}, new Exception("b1"));
        logger.log("fqcn_c1", Level.SEVERE, "c1 {0}", ExtLogRecord.FormatStyle.MESSAGE_FORMAT, new Object[]{"C1"}, new Exception("c1"));
        logger.log("fqcn_d1", Level.SEVERE, "d1", new Exception("d1"));
    }

    @Test
    public void testForcedLog() throws InterruptedException {
        final Logger logger = Logger.getLogger("a");
        final List<String> loggedMessages = new ArrayList<>();
        logger.setLevel(Level.OFF);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                loggedMessages.add(record.getMessage());
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() throws SecurityException {
                // no-op
            }
        });
        logger.setFilter(new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                return false;
            }
        });
        LogManager.setThreadLocalEffectiveLevel(Level.ALL);
        // All log messages should be handled; Even those filtered by our filter.
        callLog(logger);
        Assert.assertEquals("Bad log messages", LOG_MESSAGES, loggedMessages);
        loggedMessages.clear();

        // calling child
        callLog(Logger.getLogger("a.b"));
        Assert.assertEquals("Bad log messages", LOG_MESSAGES, loggedMessages);
        loggedMessages.clear();

        // calling unrelated logger
        callLog(Logger.getLogger("c"));
        Assert.assertEquals("Bad log messages", Collections.<String>emptyList(), loggedMessages);

        LogManager.setThreadLocalEffectiveLevel(null);

        callLog(logger);
        Assert.assertEquals("Bad log messages", Collections.<String>emptyList(), loggedMessages);
        loggedMessages.clear();

        LogManager.setThreadLocalEffectiveLevel(Level.ALL);
        callLog(logger);
        Assert.assertEquals("Bad log messages", LOG_MESSAGES, loggedMessages);
        loggedMessages.clear();
        LogManager.setThreadLocalEffectiveLevel(null);
    }

    @Test
    public void testForcedLogDiffThreads() throws InterruptedException {
        final Logger logger = Logger.getLogger("a");
        final List<String> loggedMessages = new ArrayList<>();
        logger.setLevel(Level.OFF);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                loggedMessages.add(record.getMessage());
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() throws SecurityException {
                // no-op
            }
        });
        logger.setFilter(new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                return false;
            }
        });
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                LogManager.setThreadLocalEffectiveLevel(Level.ALL);
                logger.finest("a");
            }
        });
        thread.start();
        thread.join();
        Assert.assertEquals(1, loggedMessages.size());
        Assert.assertEquals("a", loggedMessages.get(0));
        logger.finest("b");
        Assert.assertEquals(1, loggedMessages.size());
        Assert.assertEquals("a", loggedMessages.get(0));
    }

    @Test
    public void testOtherGlobalLevels() {
        final Logger logger = Logger.getLogger("a");
        final Set<Level> loggedLevels = new HashSet<>();
        logger.setLevel(Level.OFF);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                loggedLevels.add(record.getLevel());
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() throws SecurityException {
                // no-op
            }
        });
        logger.setFilter(new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                return false;
            }
        });
        LogManager.setThreadLocalEffectiveLevel(Level.CONFIG);
        logger.log(Level.SEVERE, "");
        logger.log(Level.WARNING, "");
        logger.log(Level.INFO, "");
        logger.log(Level.CONFIG, "");
        logger.log(Level.FINE, "");
        logger.log(Level.FINER, "");
        logger.log(Level.FINEST, "");
        Assert.assertEquals(
                new HashSet<Level>(Arrays.asList(Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG)),
                loggedLevels
        );
    }

}
