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
