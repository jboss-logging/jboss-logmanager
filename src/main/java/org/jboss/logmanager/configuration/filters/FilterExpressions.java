/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.configuration.filters;

import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Character.isJavaIdentifierStart;
import static java.lang.Character.isWhitespace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Filter;
import java.util.logging.Level;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.filters.AcceptAllFilter;
import org.jboss.logmanager.filters.AllFilter;
import org.jboss.logmanager.filters.AnyFilter;
import org.jboss.logmanager.filters.DenyAllFilter;
import org.jboss.logmanager.filters.InvertFilter;
import org.jboss.logmanager.filters.LevelChangingFilter;
import org.jboss.logmanager.filters.LevelFilter;
import org.jboss.logmanager.filters.LevelRangeFilter;
import org.jboss.logmanager.filters.RegexFilter;
import org.jboss.logmanager.filters.SubstituteFilter;

/**
 * Helper class to parse filter expressions.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FilterExpressions {

    private static final String ACCEPT = "accept";
    private static final String ALL = "all";
    private static final String ANY = "any";
    private static final String DENY = "deny";
    private static final String LEVELS = "levels";
    private static final String LEVEL_CHANGE = "levelChange";
    private static final String LEVEL_RANGE = "levelRange";
    private static final String MATCH = "match";
    private static final String NOT = "not";
    private static final String SUBSTITUTE = "substitute";
    private static final String SUBSTITUTE_ALL = "substituteAll";

    /**
     * Pareses a filter expression and returns the parsed filter.
     *
     * @param logContext the log context this filter is for
     * @param expression the filter expression
     *
     * @return the created filter
     */
    public static Filter parse(final LogContext logContext, final String expression) {
        final Iterator<String> iterator = tokens(expression).iterator();
        return parseFilterExpression(logContext, iterator, true);
    }

    private static Filter parseFilterExpression(final LogContext logContext, final Iterator<String> iterator,
            final boolean outermost) {
        if (!iterator.hasNext()) {
            if (outermost) {
                return null;
            }
            throw endOfExpression();
        }
        final String token = iterator.next();
        if (ACCEPT.equals(token)) {
            return AcceptAllFilter.getInstance();
        } else if (DENY.equals(token)) {
            return DenyAllFilter.getInstance();
        } else if (NOT.equals(token)) {
            expect("(", iterator);
            final Filter nested = parseFilterExpression(logContext, iterator, false);
            expect(")", iterator);
            return new InvertFilter(nested);
        } else if (ALL.equals(token)) {
            expect("(", iterator);
            final List<Filter> filters = new ArrayList<>();
            do {
                filters.add(parseFilterExpression(logContext, iterator, false));
            } while (expect(",", ")", iterator));
            return new AllFilter(filters);
        } else if (ANY.equals(token)) {
            expect("(", iterator);
            final List<Filter> filters = new ArrayList<>();
            do {
                filters.add(parseFilterExpression(logContext, iterator, false));
            } while (expect(",", ")", iterator));
            return new AnyFilter(filters);
        } else if (LEVEL_CHANGE.equals(token)) {
            expect("(", iterator);
            final Level level = logContext.getLevelForName(expectName(iterator));
            expect(")", iterator);
            return new LevelChangingFilter(level);
        } else if (LEVELS.equals(token)) {
            expect("(", iterator);
            final Set<Level> levels = new HashSet<>();
            do {
                levels.add(logContext.getLevelForName(expectName(iterator)));
            } while (expect(",", ")", iterator));
            return new LevelFilter(levels);
        } else if (LEVEL_RANGE.equals(token)) {
            final boolean minInclusive = expect("[", "(", iterator);
            final Level minLevel = logContext.getLevelForName(expectName(iterator));
            expect(",", iterator);
            final Level maxLevel = logContext.getLevelForName(expectName(iterator));
            final boolean maxInclusive = expect("]", ")", iterator);
            return new LevelRangeFilter(minLevel, minInclusive, maxLevel, maxInclusive);
        } else if (MATCH.equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(")", iterator);
            return new RegexFilter(pattern);
        } else if (SUBSTITUTE.equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(",", iterator);
            final String replacement = expectString(iterator);
            expect(")", iterator);
            return new SubstituteFilter(pattern, replacement, false);
        } else if (SUBSTITUTE_ALL.equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(",", iterator);
            final String replacement = expectString(iterator);
            expect(")", iterator);
            return new SubstituteFilter(pattern, replacement, true);
        } else {
            final String name = expectName(iterator);
            throw new IllegalArgumentException(String.format("No filter named \"%s\" is defined", name));
        }
    }

    private static String expectName(Iterator<String> iterator) {
        if (iterator.hasNext()) {
            final String next = iterator.next();
            if (isJavaIdentifierStart(next.codePointAt(0))) {
                return next;
            }
        }
        throw new IllegalArgumentException("Expected identifier next in filter expression");
    }

    private static String expectString(final Iterator<String> iterator) {
        if (iterator.hasNext()) {
            final String next = iterator.next();
            if (next.codePointAt(0) == '"') {
                return next.substring(1);
            }
        }
        throw new IllegalArgumentException("Expected string next in filter expression");
    }

    private static boolean expect(final String trueToken, final String falseToken, final Iterator<String> iterator) {
        final boolean hasNext = iterator.hasNext();
        final String next = hasNext ? iterator.next() : null;
        final boolean result;
        if (!hasNext || !((result = trueToken.equals(next)) || falseToken.equals(next))) {
            throw new IllegalArgumentException(
                    "Expected '" + trueToken + "' or '" + falseToken + "' next in filter expression");
        }
        return result;
    }

    private static void expect(String token, Iterator<String> iterator) {
        if (!iterator.hasNext() || !token.equals(iterator.next())) {
            throw new IllegalArgumentException("Expected '" + token + "' next in filter expression");
        }
    }

    private static IllegalArgumentException endOfExpression() {
        return new IllegalArgumentException("Unexpected end of filter expression");
    }

    @SuppressWarnings("UnusedAssignment")
    private static List<String> tokens(final String source) {
        final List<String> tokens = new ArrayList<>();
        final int length = source.length();
        int idx = 0;
        while (idx < length) {
            int ch;
            ch = source.codePointAt(idx);
            if (isWhitespace(ch)) {
                ch = source.codePointAt(idx);
                idx = source.offsetByCodePoints(idx, 1);
            } else if (isJavaIdentifierStart(ch)) {
                int start = idx;
                do {
                    idx = source.offsetByCodePoints(idx, 1);
                } while (idx < length && isJavaIdentifierPart(ch = source.codePointAt(idx)));
                tokens.add(source.substring(start, idx));
            } else if (ch == '"') {
                final StringBuilder b = new StringBuilder();
                // tag token as a string
                b.append('"');
                idx = source.offsetByCodePoints(idx, 1);
                while (idx < length && (ch = source.codePointAt(idx)) != '"') {
                    ch = source.codePointAt(idx);
                    if (ch == '\\') {
                        idx = source.offsetByCodePoints(idx, 1);
                        if (idx == length) {
                            throw new IllegalArgumentException("Truncated filter expression string");
                        }
                        ch = source.codePointAt(idx);
                        switch (ch) {
                            case '\\':
                                b.append('\\');
                                break;
                            case '\'':
                                b.append('\'');
                                break;
                            case '"':
                                b.append('"');
                                break;
                            case 'b':
                                b.append('\b');
                                break;
                            case 'f':
                                b.append('\f');
                                break;
                            case 'n':
                                b.append('\n');
                                break;
                            case 'r':
                                b.append('\r');
                                break;
                            case 't':
                                b.append('\t');
                                break;
                            default:
                                throw new IllegalArgumentException("Invalid escape found in filter expression string");
                        }
                    } else {
                        b.appendCodePoint(ch);
                    }
                    idx = source.offsetByCodePoints(idx, 1);
                }
                idx = source.offsetByCodePoints(idx, 1);
                tokens.add(b.toString());
            } else {
                int start = idx;
                idx = source.offsetByCodePoints(idx, 1);
                tokens.add(source.substring(start, idx));
            }
        }
        return tokens;
    }
}
