/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.ext;

import org.jboss.logmanager.LogContext;

import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 *
 */
public final class AutoInstaller {
    private AutoInstaller() {}

    /**
     * Install the given log context into the log manager.
     *
     * @param logManager the log manager (must not be {@code null})
     * @param logContext the log context (must not be {@code null})
     * @return {@code true} if the context was installed, or {@code false} if it was already installed
     */
    public static boolean installInto(LogManager logManager, LogContext logContext) {
        if (logManager instanceof org.jboss.logmanager.LogManager) {
            return false;
        }
        final Logger rootLogger = logManager.getLogger("");
        final Handler[] existingHandlers = rootLogger.getHandlers();
        for (Handler existingHandler : existingHandlers) {
            if (existingHandler instanceof InstallerHandler) {
                return false;
            }
        }
        rootLogger.addHandler(new InstallerHandler(logManager, logContext));
        return true;
    }

    static final class InstallerHandler extends Handler {
        private final LogManager logManager;
        private final LogContext logContext;

        InstallerHandler(LogManager logManager, LogContext logContext) {
            this.logManager = logManager;
            this.logContext = logContext;
        }

        @Override
        public void publish(LogRecord record) {
            final String category = record.getLoggerName();
            final Logger logger = logManager.getLogger(category);
            final RedirectHandler redirectHandler = getOrInstall(logger);
            redirectHandler.publish(record);
        }

        private RedirectHandler getOrInstall(Logger logger) {
            synchronized (this) {
                for (Handler handler : logger.getHandlers()) {
                    if (handler instanceof RedirectHandler) {
                        logger.setUseParentHandlers(false);
                        return (RedirectHandler) handler;
                    }
                }
                RedirectHandler redirectHandler = new RedirectHandler(logContext.getLogger(logger.getName()));
                logger.addHandler(redirectHandler);
                logger.setUseParentHandlers(false);
                return redirectHandler;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    static final class RedirectHandler extends Handler {
        private final org.jboss.logmanager.Logger logger;

        RedirectHandler(org.jboss.logmanager.Logger logger) {
            this.logger = logger;
        }

        @Override
        public void publish(LogRecord record) {
            logger.log(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
