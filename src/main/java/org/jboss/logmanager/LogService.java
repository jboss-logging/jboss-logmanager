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