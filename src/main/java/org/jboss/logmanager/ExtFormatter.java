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

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A formatter which handles {@link org.jboss.logmanager.ExtLogRecord ExtLogRecord} instances.
 */
public abstract class ExtFormatter extends Formatter {

    /** {@inheritDoc} */
    @Override
    public final String format(final LogRecord record) {
        return format(ExtLogRecord.wrap(record));
    }

    /**
     * Format a message using an extended log record.
     *
     * @param extLogRecord the log record
     * @return the formatted message
     */
    public abstract String format(final ExtLogRecord extLogRecord);
}
