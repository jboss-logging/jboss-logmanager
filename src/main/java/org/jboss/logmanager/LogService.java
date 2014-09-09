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

import java.util.logging.Handler;

/**
 * A simple log service which can be used to remove any bootstrap handlers when a real handler is configured.
 */
public final class LogService {

    private static final Logger log = Logger.getLogger("org.jboss.logging.service");
    private static final Logger rootLogger = Logger.getLogger("");

    private Handler[] bootstrapHandlers;

    /**
     * Create lifecycle method.
     */
    public void create() {
    }

    /**
     * Start method; removes and saves bootstrap handlers.
     */
    public void start() {
        log.info("Removing bootstrap log handlers");
        final Handler[] bootstrapHandlers = rootLogger.clearHandlers();
        this.bootstrapHandlers = bootstrapHandlers;
        for (Handler handler : bootstrapHandlers) {
            safeFlush(handler);
        }
        // this message probably won't appear anywhere...
        log.info("Removed bootstrap log handlers");
    }

    /**
     * Stop method; removes root handlers and restores the bootstrap handlers.
     */
    public void stop() {
        // this message probably won't appear anywhere...
        log.info("Restoring bootstrap log handlers");
        // clear any remaining handlers from the root (there shouldn't be any, but...)
        for (Handler handler : rootLogger.clearHandlers()) {
            safeClose(handler);
        }
        final Handler[] bootstrapHandlers = this.bootstrapHandlers;
        this.bootstrapHandlers = null;
        for (Handler handler : bootstrapHandlers) {
            rootLogger.addHandler(handler);
        }
        log.info("Restored bootstrap log handlers");
    }

    private static void safeFlush(Handler handler) {
        try {
            if (handler != null) handler.flush();
        } catch (Throwable t) {
            // todo - might this loop somehow?
            log.log(Level.ERROR, "Error flushing a log handler", t);
        }
    }

    private static void safeClose(Handler handler) {
        try {
            if (handler != null) handler.close();
        } catch (Throwable t) {
            // todo - might this loop somehow?
            log.log(Level.ERROR, "Error closing a log handler", t);
        }
    }

    /**
     * Destroy lifecycle method.
     */
    public void destroy() {
    }
}