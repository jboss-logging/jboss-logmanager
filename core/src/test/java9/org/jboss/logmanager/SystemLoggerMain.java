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

import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SystemLoggerMain {

    public static void main(final String[] args) {
        if (Boolean.getBoolean("system.logger.test.jul")) {
            // Access the log manager to ensure it's configured before the system property is set
            LogManager.getLogManager();
        }

        final System.Logger.Level level = System.Logger.Level.valueOf(System.getProperty("system.logger.test.level", "INFO"));
        final String msgId = System.getProperty("system.logger.test.msg.id");
        final String msg = String.format("Test message from %s id %s", SystemLoggerMain.class.getName(), msgId);
        final System.Logger systemLogger = System.getLogger(SystemLoggerMain.class.getName());
        MDC.put("logger.type", systemLogger.getClass().getName());
        MDC.put("java.util.logging.LogManager", LogManager.getLogManager().getClass().getName());
        MDC.put("java.util.logging.manager", System.getProperty("java.util.logging.manager"));
        systemLogger.log(level, msg);

        final Logger logger = Logger.getLogger(SystemLoggerMain.class.getName());
        MDC.put("logger.type", logger.getClass().getName());
        MDC.put("java.util.logging.LogManager", LogManager.getLogManager().getClass().getName());
        MDC.put("java.util.logging.manager", System.getProperty("java.util.logging.manager"));
        logger.info(msg);
    }
}