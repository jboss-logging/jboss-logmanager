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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A container-friendly service which will manage the installation of a
 * {@link LogContextSelector} into the log system.  Only one such service
 * may be active at a time, or an error will result.
 */
public final class LogContextSelectorService {
    private LogContextSelector selector;
    private static final AtomicBoolean oneInstalled = new AtomicBoolean();

    /**
     * Get the selector to install.
     *
     * @return the selector
     */
    public LogContextSelector getSelector() {
        return selector;
    }

    /**
     * Set the selector to install.
     *
     * @param selector the selector
     */
    public void setSelector(final LogContextSelector selector) {
        this.selector = selector;
    }

    /**
     * Install the selector.
     */
    public void start() {
        if (oneInstalled.getAndSet(true)) {
            throw new IllegalStateException("A log context selector is already installed");
        }
        LogContext.setLogContextSelector(selector);
    }

    /**
     * Uninstall the selector.
     */
    public void stop() {
        if (oneInstalled.getAndSet(false)) {
            LogContext.setLogContextSelector(LogContext.DEFAULT_LOG_CONTEXT_SELECTOR);
        }
    }
}
