/*
 * JBoss, Home of Professional Open Source.
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

import java.io.InputStream;

/**
 * A configurator for a log context.  A log context configurator should set up all the log categories,
 * handlers, formatters, filters, attachments, and other constructs as specified by the configuration.
 */
public interface LogContextConfigurator {
    /**
     * Configure the given log context according to this configurator's policy.  If a configuration stream was
     * provided, that is passed in to this method to be used or ignored.  The stream should remain open after
     * this method is called.
     *
     * @param logContext the log context to configure (not {@code null})
     * @param inputStream the input stream that was requested to be used, or {@code null} if none was provided
     */
    void configure(LogContext logContext, InputStream inputStream);

    /**
     * A constant representing an empty configuration.  The configurator does nothing.
     */
    LogContextConfigurator EMPTY = new LogContextConfigurator() {
        @Override
        public void configure(LogContext logContext, InputStream inputStream) {
        }
    };
}
