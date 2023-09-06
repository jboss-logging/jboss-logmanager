/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.formatters;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Standard date formats.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
enum StandardDateFormat {
    /**
     * Format: {@code HH:mm:ss,SSS}
     */
    ABSOLUTE("HH:mm:ss,SSS"),
    /**
     * Format: {@code yyyyMMddHHmmssSSS}
     */
    COMPACT("yyyyMMddHHmmssSSS"),
    /**
     * Format: {@code yyyy-MM-dd HH:mm:ss,SSS}
     */
    DEFAULT("yyyy-MM-dd HH:mm:ss,SSS"),
    /**
     * Format: {@code yyyy-MM-dd'T'HH:mm:ss,SSS}
     */
    ISO8601("yyyy-MM-dd'T'HH:mm:ss,SSS"),
    ;

    private final DateTimeFormatter formatter;
    private final String pattern;

    StandardDateFormat(final String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
        this.pattern = pattern;
    }

    /**
     * Resolves the requested date format based on the pattern. If the pattern is {@code null}, then the {@link #DEFAULT}
     * pattern is used. If the pattern matches one of the constants, that format will be returned. Otherwise, the
     * pattern is assumed to be a valid {@link DateTimeFormatter#ofPattern(String)} format pattern}.
     *
     * @param pattern the pattern to return the formatter for
     *
     * @return the formatter used to format timestamps
     */
    static DateTimeFormatter resolve(final String pattern) {
        if (pattern == null) {
            return DEFAULT.formatter;
        }
        for (StandardDateFormat constant : values()) {
            if (pattern.toUpperCase(Locale.ROOT).equals(constant.name()) || pattern.equals(constant.pattern)) {
                return constant.formatter;
            }
        }
        return DateTimeFormatter.ofPattern(pattern);
    }
}
