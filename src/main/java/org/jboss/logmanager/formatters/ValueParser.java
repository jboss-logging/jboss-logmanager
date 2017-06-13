/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.logmanager.config.ValueExpression;

/**
 * A utility for parsing string values into objects.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */

class ValueParser {

    private static final int KEY = 0;
    private static final int VALUE = 1;

    /**
     * Parses a string of key/value pairs into a map.
     * <p>
     * The key/value pairs are separated by a comma ({@code ,}). The key and value are separated by an equals
     * ({@code =}).
     * </p>
     * <p>
     * If a key contains a {@code \} or an {@code =} it must be escaped by a preceding {@code \}. Example: {@code
     * key\==value,\\key=value}.
     * </p>
     * <p>
     * If a value contains a {@code \} or a {@code ,} it must be escaped by a preceding {@code \}. Example: {@code
     * key=part1\,part2,key2=value\\other}.
     * </p>
     *
     * <p>
     * If the value for a key is empty there is no trailing {@code =} after a key the value is assigned an empty
     * string.
     * </p>
     *
     * @param s the string to parse
     *
     * @return a map of the key value pairs or an empty map if the string is {@code null} or empty
     */
    static Map<String, String> stringToMap(final String s) {
        if (s == null || s.isEmpty()) return Collections.emptyMap();

        final Map<String, String> map = new LinkedHashMap<>();

        final StringBuilder key = new StringBuilder();
        final StringBuilder value = new StringBuilder();
        final char[] chars = s.toCharArray();
        int state = 0;
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            switch (state) {
                case KEY: {
                    switch (c) {
                        case '\\': {
                            // Handle escapes
                            if (chars.length > ++i) {
                                final char next = chars[i];
                                if (next == '=' || next == '\\') {
                                    key.append(next);
                                    continue;
                                }
                            }
                            throw new IllegalStateException("Escape character found at invalid position " + i + ". Only characters '=' and '\\' need to be escaped for a key.");
                        }
                        case '=': {
                            state = VALUE;
                            continue;
                        }
                        default: {
                            key.append(c);
                            continue;
                        }
                    }
                }
                case VALUE: {
                    switch (c) {
                        case '\\': {
                            // Handle escapes
                            if (chars.length > ++i) {
                                final char next = chars[i];
                                if (next == ',' || next == '\\') {
                                    value.append(next);
                                    continue;
                                }
                            }
                            throw new IllegalStateException("Escape character found at invalid position " + i + ". Only characters ',' and '\\' need to be escaped for a value.");
                        }
                        case ',': {
                            // Only add if the key isn't empty
                            if (key.length() > 0) {
                                // Values may be expressions
                                final ValueExpression<String> valueExpression = ValueExpression.STRING_RESOLVER.resolve(value.toString());
                                map.put(key.toString(), valueExpression.getResolvedValue());
                                // Clear the key
                                key.setLength(0);
                            }
                            // Clear the value
                            value.setLength(0);
                            state = KEY;
                            continue;
                        }
                        default: {
                            value.append(c);
                            continue;
                        }
                    }
                }
                default:
                    // not reachable
                    throw new IllegalStateException();
            }
        }
        // Add the last entry
        if (key.length() > 0) {
            // Values may be expressions
            final ValueExpression<String> valueExpression = ValueExpression.STRING_RESOLVER.resolve(value.toString());
            map.put(key.toString(), valueExpression.getResolvedValue());
        }
        return Collections.unmodifiableMap(map);
    }
}
