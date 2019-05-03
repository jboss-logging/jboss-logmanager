/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.EnumMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JBossLoggerFinder extends System.LoggerFinder {
    private static final Map<System.Logger.Level, java.util.logging.Level> LEVELS = new EnumMap<>(System.Logger.Level.class);
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static volatile boolean PROPERTY_SET = false;

    static {
        LEVELS.put(System.Logger.Level.ALL, Level.ALL);
        LEVELS.put(System.Logger.Level.TRACE, Level.TRACE);
        LEVELS.put(System.Logger.Level.DEBUG, Level.DEBUG);
        LEVELS.put(System.Logger.Level.INFO, Level.INFO);
        LEVELS.put(System.Logger.Level.WARNING, Level.WARN);
        LEVELS.put(System.Logger.Level.ERROR, Level.ERROR);
        LEVELS.put(System.Logger.Level.OFF, Level.OFF);
    }

    @Override
    public System.Logger getLogger(final String name, final Module module) {
        if (!PROPERTY_SET) {
            synchronized (this) {
                if (!PROPERTY_SET) {
                    if (System.getSecurityManager() == null) {
                        if (System.getProperty("java.util.logging.manager") == null) {
                            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
                        }
                    } else {
                        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                            if (System.getProperty("java.util.logging.manager") == null) {
                                System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
                            }
                            return null;
                        });
                    }
                }
                PROPERTY_SET = true;
            }
        }
        final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
        if (!(logger instanceof org.jboss.logmanager.Logger)) {
            if (LOGGED.compareAndSet(false, true)) {
                logger.log(Level.ERROR, "The LogManager accessed before the \"java.util.logging.manager\" system property was set to \"org.jboss.logmanager.LogManager\". Results may be unexpected.");
            }
        }
        return new JBossSystemLogger(logger);
    }

    private static class JBossSystemLogger implements System.Logger {
        private static final String LOGGER_CLASS_NAME = JBossSystemLogger.class.getName();
        private final java.util.logging.Logger delegate;

        private JBossSystemLogger(final java.util.logging.Logger delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public boolean isLoggable(final Level level) {
            return delegate.isLoggable(LEVELS.getOrDefault(level, java.util.logging.Level.INFO));
        }

        @Override
        public void log(final Level level, final ResourceBundle bundle, final String msg, final Throwable thrown) {
            final ExtLogRecord record = new ExtLogRecord(LEVELS.getOrDefault(level, java.util.logging.Level.INFO), msg, LOGGER_CLASS_NAME);
            record.setThrown(thrown);
            record.setResourceBundle(bundle);
            delegate.log(record);
        }

        @Override
        public void log(final Level level, final ResourceBundle bundle, final String format, final Object... params) {
            final ExtLogRecord record = new ExtLogRecord(LEVELS.getOrDefault(level, java.util.logging.Level.INFO), format, ExtLogRecord.FormatStyle.MESSAGE_FORMAT, LOGGER_CLASS_NAME);
            record.setParameters(params);
            record.setResourceBundle(bundle);
            delegate.log(record);
        }
    }
}