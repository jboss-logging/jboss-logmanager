/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
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

package org.jboss.logmanager.formatters;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jboss.logmanager.ExtLogRecord;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import static java.lang.Math.min;
import static java.lang.Math.max;
import static java.lang.System.getSecurityManager;
import static java.lang.Thread.currentThread;
import static java.security.AccessController.doPrivileged;

import java.io.PrintWriter;
import java.util.regex.Pattern;

/**
 * Formatter utility methods.
 */
public final class Formatters {

    public static final String THREAD_ID = "id";

    private static final boolean DEFAULT_TRUNCATE_BEGINNING = false;
    private static final String NEW_LINE = String.format("%n");
    private static final Pattern PRECISION_INT_PATTERN = Pattern.compile("\\d+");


    private Formatters() {
    }

    private static final Formatter NULL_FORMATTER = new Formatter() {
        public String format(final LogRecord record) {
            return "";
        }
    };

    /**
     * Get the null formatter, which outputs nothing.
     *
     * @return the null formatter
     */
    public static Formatter nullFormatter() {
        return NULL_FORMATTER;
    }

    /**
     * Create a format step which simply emits the given string.
     *
     * @param string the string to emit
     * @return a format step
     */
    public static FormatStep textFormatStep(final String string) {
        return new FormatStep() {
            public void render(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(string);
            }

            public int estimateLength() {
                return string.length();
            }
        };
    }

    /**
     * Apply up to {@code count} trailing segments of the given string to the given {@code builder}.
     *
     * @param count the maximum number of segments to include
     * @param subject the subject string
     * @return the substring
     */
    private static String applySegments(final int count, final String subject) {
        if (count == 0) {
            return subject;
        }
        int idx = subject.length() + 1;
        for (int i = 0; i < count; i ++) {
            idx = subject.lastIndexOf('.', idx - 1);
            if (idx == -1) {
                return subject;
            }
        }
        return subject.substring(idx + 1);
    }

    /**
     * Apply up to {@code precision} trailing segments of the given string to the given {@code builder}. If the
     * precision contains non-integer values
     *
     * @param precision the precision used to
     * @param subject   the subject string
     *
     * @return the substring
     */
    private static String applySegments(final String precision, final String subject) {
        if (precision == null) {
            return subject;
        }

        // Check for dots
        if (PRECISION_INT_PATTERN.matcher(precision).matches()) {
            return applySegments(Integer.parseInt(precision), subject);
        }
        // %c{1.} would be o.j.l.f.FormatStringParser
        // %c{1.~} would be o.~.~.~.FormatStringParser
        // %c{.} ....FormatStringParser
        final Map<Integer, Segment> segments = parsePatternSegments(precision);
        final Deque<String> categorySegments = parseCategorySegments(subject);
        final StringBuilder result = new StringBuilder();
        Segment segment = null;
        int index = 0;
        while (true) {
            index++;
            if (segments.containsKey(index)) {
                segment = segments.get(index);
            }
            final String s = categorySegments.poll();
            // Always print the last part of the category segments
            if (categorySegments.peek() == null) {
                result.append(s);
                break;
            }
            if (segment == null) {
                result.append(s).append('.');
            } else {
                if (segment.len > 0) {
                    if (segment.len > s.length()) {
                        result.append(s);
                    } else {
                        result.append(s.substring(0, segment.len));
                    }
                }
                if (segment.text != null) {
                    result.append(segment.text);
                }
                result.append('.');
            }
        }
        return result.toString();
    }

    private abstract static class JustifyingFormatStep implements FormatStep {
        private final boolean leftJustify;
        private final boolean truncateBeginning;
        private final int minimumWidth;
        private final int maximumWidth;

        protected JustifyingFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
            if (maximumWidth != 0 && minimumWidth > maximumWidth) {
                throw new IllegalArgumentException("Specified minimum width may not be greater than the specified maximum width");
            }
            if (maximumWidth < 0 || minimumWidth < 0) {
                throw new IllegalArgumentException("Minimum and maximum widths must not be less than zero");
            }
            this.leftJustify = leftJustify;
            this.truncateBeginning = truncateBeginning;
            this.minimumWidth = minimumWidth;
            this.maximumWidth = maximumWidth == 0 ? Integer.MAX_VALUE : maximumWidth;
        }

        public void render(final StringBuilder builder, final ExtLogRecord record) {
            final int minimumWidth = this.minimumWidth;
            final int maximumWidth = this.maximumWidth;
            final boolean leftJustify = this.leftJustify;
            if (leftJustify) {
                // no copy necessary for left justification
                final int oldLen = builder.length();
                renderRaw(builder, record);
                final int newLen = builder.length();
                // if we exceeded the max width, chop it off
                final int writtenLen = newLen - oldLen;
                final int overflow = writtenLen - maximumWidth;
                if (overflow > 0) {
                    if (truncateBeginning) {
                        builder.delete(oldLen, overflow + 1);
                    }
                    builder.setLength(newLen - overflow);
                } else {
                    final int spaces = minimumWidth - writtenLen;
                    for (int i = 0; i < spaces; i ++) {
                        builder.append(' ');
                    }
                }
            } else {
                // only copy the data if we're right justified
                final StringBuilder subBuilder = new StringBuilder();
                renderRaw(subBuilder, record);
                final int len = subBuilder.length();
                if (len > maximumWidth) {
                    if (truncateBeginning) {
                        final int overflow = len - maximumWidth;
                        subBuilder.delete(0, overflow);
                    }
                    subBuilder.setLength(maximumWidth);
                } else if (len < minimumWidth) {
                    // right justify
                    int spaces = minimumWidth - len;
                    for (int i = 0; i < spaces; i ++) {
                        builder.append(' ');
                    }
                }
                builder.append(subBuilder);
            }
        }

        public int estimateLength() {
            final int maximumWidth = this.maximumWidth;
            final int minimumWidth = this.minimumWidth;
            if (maximumWidth != 0) {
                return min(maximumWidth, minimumWidth * 3);
            } else {
                return max(32, minimumWidth);
            }
        }

        public abstract void renderRaw(final StringBuilder builder, final ExtLogRecord record);
    }

    private abstract static class SegmentedFormatStep extends JustifyingFormatStep {
        private final int count;
        private final String precision;

        protected SegmentedFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth, final int count) {
            super(leftJustify, minimumWidth, truncateBeginning, maximumWidth);
            this.count = count;
            precision = null;
        }

        protected SegmentedFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth, final String precision) {
            super(leftJustify, minimumWidth, truncateBeginning, maximumWidth);
            this.count = 0;
            this.precision = precision;
        }

        public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
            if (precision == null) {
                builder.append(applySegments(count, getSegmentedSubject(record)));
            } else {
                builder.append(applySegments(precision, getSegmentedSubject(record)));
            }
        }

        public abstract String getSegmentedSubject(final ExtLogRecord record);
    }

    /**
     * Create a format step which emits the logger name with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param precision    the argument used for the logger name, may be {@code null} or contain dots to format the logger name
     * @return the format
     */
    public static FormatStep loggerNameFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth, final String precision) {
        return loggerNameFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth, precision);
    }

    /**
     * Create a format step which emits the logger name with the given justification rules.
     *
     * @param leftJustify       {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth      the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth      the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param precision         the argument used for the logger name, may be {@code null} or contain dots to format the
     *                          logger name
     *
     * @return the format
     */
    public static FormatStep loggerNameFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth, final String precision) {
        return new SegmentedFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth, precision) {
            public String getSegmentedSubject(final ExtLogRecord record) {
                return record.getLoggerName();
            }
        };
    }

    /**
     * Create a format step which emits the source class name with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param precision    the argument used for the class name, may be {@code null} or contain dots to format the class name
     * @return the format step
     */
    public static FormatStep classNameFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth, final String precision) {
        return classNameFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth, precision);
    }

    /**
     * Create a format step which emits the source class name with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify       {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth      the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth      the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param precision         the argument used for the class name, may be {@code null} or contain dots to format the
     *                          class name
     *
     * @return the format step
     */
    public static FormatStep classNameFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth, final String precision) {
        return new SegmentedFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth, precision) {
            public String getSegmentedSubject(final ExtLogRecord record) {
                return record.getSourceClassName();
            }
        };
    }

    /**
     * Create a format step which emits the date of the log record with the given justification rules.
     *
     * @param timeZone the time zone to format to
     * @param formatString the date format string
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep dateFormatStep(final TimeZone timeZone, final String formatString, final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return dateFormatStep(timeZone, formatString, leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the date of the log record with the given justification rules.
     *
     * @param timeZone          the time zone to format to
     * @param formatString      the date format string
     * @param leftJustify       {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth      the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth      the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     *
     * @return the format step
     */
    public static FormatStep dateFormatStep(final TimeZone timeZone, final String formatString, final boolean leftJustify, final int minimumWidth,
                                            final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            private final ThreadLocal<SimpleDateFormat> holder = new ThreadLocal<SimpleDateFormat>() {
                protected SimpleDateFormat initialValue() {
                    final SimpleDateFormat dateFormat = new SimpleDateFormat(formatString == null ? "yyyy-MM-dd HH:mm:ss,SSS" : formatString);
                    dateFormat.setTimeZone(timeZone);
                    return dateFormat;
                }
            };

            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(holder.get().format(new Date(record.getMillis())));
            }
        };
    }

    /**
     * Create a format step which emits the date of the log record with the given justification rules.
     *
     * @param formatString the date format string
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep dateFormatStep(final String formatString, final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return dateFormatStep(TimeZone.getDefault(), formatString, leftJustify, minimumWidth, maximumWidth);
    }

    /**
     * Create a format step which emits the source file name with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep fileNameFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return fileNameFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the source file name with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep fileNameFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getSourceFileName());
            }
        };
    }

    /**
     * Create a format step which emits the hostname.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param qualified {@code true} to use the fully qualified host name, {@code false} to only use the
     * @return the format step
     */
    public static FormatStep hostnameFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth, final boolean qualified) {
        final Properties props;
        final Map<String, String> env;
        if (System.getSecurityManager() == null) {
            props = System.getProperties();
            env = System.getenv();
        } else {
            props = AccessController.doPrivileged(new PrivilegedAction<Properties>() {
                @Override
                public Properties run() {
                    return System.getProperties();
                }
            });
            env = AccessController.doPrivileged(new PrivilegedAction<Map<String, String>>() {
                @Override
                public Map<String, String> run() {
                    return System.getenv();
                }
            });
        }
        final String hostname = findHostname(props, env, qualified);
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(hostname);
            }
        };
    }

    /**
     * Create a format step which emits the complete source location information with the given justification rules
     * (NOTE: call stack introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep locationInformationFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return locationInformationFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the complete source location information with the given justification rules
     * (NOTE: call stack introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep locationInformationFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                final String fileName = record.getSourceFileName();
                final int lineNumber = record.getSourceLineNumber();
                final String className = record.getSourceClassName();
                final String methodName = record.getSourceMethodName();
                builder.append(className).append('.').append(methodName);
                builder.append('(').append(fileName);
                if (lineNumber != -1) {
                    builder.append(':').append(lineNumber);
                }
                builder.append(')');
            }
        };
    }

    /**
     * Create a format step which emits the source file line number with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep lineNumberFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return lineNumberFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the source file line number with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep lineNumberFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getSourceLineNumber());
            }
        };
    }

    /**
     * Create a format step which emits the formatted log message text with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep messageFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return messageFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the formatted log message text with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep messageFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getFormattedMessage());
                final Throwable t = record.getThrown();
                if (t != null) {
                    builder.append(": ");
                    t.printStackTrace(new PrintWriter(new StringBuilderWriter(builder)));
                }
            }
        };
    }

    /**
     * Create a format step which emits the formatted log message text (simple version, no exception traces) with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep simpleMessageFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return simpleMessageFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the formatted log message text (simple version, no exception traces) with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep simpleMessageFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getFormattedMessage());
            }
        };
    }

    /**
     * Create a format step which emits the stack trace of an exception with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param extended {@code true} if the stack trace should attempt to include extended JAR version information
     * @return the format step
     */
    public static FormatStep exceptionFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth, final boolean extended) {
        return exceptionFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth, extended);
    }

    /**
     * Create a format step which emits the stack trace of an exception with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param extended {@code true} if the stack trace should attempt to include extended JAR version information
     * @return the format step
     */
    public static FormatStep exceptionFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth, final boolean extended) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        final Throwable t = record.getThrown();
                        if (t != null) {
                            builder.append(": ").append(t).append(NEW_LINE);
                            final StackTraceElement[] stackTrace = t.getStackTrace();
                            final Map<String, String> cache = extended ? new HashMap<String, String>() : null;
                            if (extended) {
                                for (StackTraceElement element : stackTrace) {
                                    renderExtended(builder, element, cache);
                                }
                            } else {
                                for (StackTraceElement element : stackTrace) {
                                    renderTrivial(builder, element);
                                }
                            }
                            final Throwable cause = t.getCause();
                            if (cause != null) {
                                renderCause(builder, t, cause, cache, extended);
                            }
                        }
                        return null;
                    }
                });
            }

            private void renderTrivial(final StringBuilder builder, final StackTraceElement element) {
                builder.append("\tat ").append(element).append(NEW_LINE);
            }

            private void renderExtended(final StringBuilder builder, final StackTraceElement element, final Map<String, String> cache) {
                builder.append("\tat ").append(element);
                final String className = element.getClassName();
                final String cached;
                if ((cached = cache.get(className)) != null) {
                    builder.append(cached).append(NEW_LINE);
                    return;
                }
                final int dotIdx = className.lastIndexOf('.');
                if (dotIdx == -1) {
                    builder.append(NEW_LINE);
                    return;
                }
                final String packageName = className.substring(0, dotIdx);

                // try to guess the real Class object
                final Class<?> exceptionClass = guessClass(className);

                // now try to guess the real Package object
                Package exceptionPackage = null;
                if (exceptionClass != null) {
                    exceptionPackage = exceptionClass.getPackage();
                }
                if (exceptionPackage == null) try {
                    exceptionPackage = Package.getPackage(packageName);
                } catch (Throwable t) {
                    // ignore
                }

                // now try to extract the version from the Package
                String packageVersion = null;
                if (exceptionPackage != null) {
                    try {
                        packageVersion = exceptionPackage.getImplementationVersion();
                    } catch (Throwable t) {
                        // ignore
                    }
                    if (packageVersion == null) try {
                        packageVersion = exceptionPackage.getSpecificationVersion();
                    } catch (Throwable t) {
                        // ignore
                    }
                }

                // now try to find the originating resource of the class
                URL resource = null;
                final SecurityManager sm = getSecurityManager();
                final String classResourceName = className.replace('.', '/') + ".class";
                if (exceptionClass != null) {
                    try {
                        if (sm == null) {
                            final ProtectionDomain protectionDomain = exceptionClass.getProtectionDomain();
                            if (protectionDomain != null) {
                                final CodeSource codeSource = protectionDomain.getCodeSource();
                                if (codeSource != null) {
                                    resource = codeSource.getLocation();
                                }
                            }
                        } else {
                            resource = doPrivileged(new PrivilegedAction<URL>() {
                                public URL run() {
                                    final ProtectionDomain protectionDomain = exceptionClass.getProtectionDomain();
                                    if (protectionDomain != null) {
                                        final CodeSource codeSource = protectionDomain.getCodeSource();
                                        if (codeSource != null) {
                                            return codeSource.getLocation();
                                        }
                                    }
                                    return null;
                                }
                            });
                        }
                    } catch (Throwable t) {
                        // ignore
                    }
                    if (resource == null) try {
                        final ClassLoader exceptionClassLoader = exceptionClass.getClassLoader();
                        if (sm == null) {
                            resource = exceptionClassLoader == null ? ClassLoader.getSystemResource(classResourceName) : exceptionClassLoader.getResource(classResourceName);
                        } else {
                            resource = doPrivileged(new PrivilegedAction<URL>() {
                                public URL run() {
                                    return exceptionClassLoader == null ? ClassLoader.getSystemResource(classResourceName) : exceptionClassLoader.getResource(classResourceName);
                                }
                            });
                        }
                    } catch (Throwable t) {
                        // ignore
                    }
                }

                // now try to extract the JAR name from the resource URL
                String jarName = getJarName(resource, classResourceName);

                // finally, render the mess
                boolean started = false;
                final StringBuilder tagBuilder = new StringBuilder();
                if (jarName != null) {
                    started = true;
                    tagBuilder.append(" [").append(jarName).append(':');
                }
                if (packageVersion != null) {
                    if (! started) {
                        tagBuilder.append(" [:");
                        started = true;
                    }
                    tagBuilder.append(packageVersion);
                }
                if (started) {
                    tagBuilder.append(']');
                    final String tag = tagBuilder.toString();
                    cache.put(className, tag);
                    builder.append(tag);
                } else {
                    cache.put(className, "");
                }
                builder.append(NEW_LINE);
            }

            private Class<?> guessClass(final String name) {
                try {
                    try {
                        final ClassLoader tccl = currentThread().getContextClassLoader();
                        if (tccl != null) return Class.forName(name, false, tccl);
                    } catch (ClassNotFoundException e) {
                        // ok, try something else...
                    }
                    try {
                        return Class.forName(name);
                    } catch (ClassNotFoundException e) {
                        // ok, try something else...
                    }
                    return Class.forName(name, false, null);
                } catch (Throwable t) {
                    return null;
                }
            }

            private void renderCause(final StringBuilder builder, final Throwable t, final Throwable cause, final Map<String, String> cache, final boolean extended) {

                final StackTraceElement[] causeStack = cause.getStackTrace();
                final StackTraceElement[] currentStack = t.getStackTrace();

                int m = causeStack.length - 1;
                int n = currentStack.length - 1;

                // Walk the stacks backwards from the end, until we find an element that is different
                while (m >= 0 && n >= 0 && causeStack[m].equals(currentStack[n])) {
                    m--; n--;
                }
                int framesInCommon = causeStack.length - 1 - m;

                builder.append("Caused by: ").append(cause).append(NEW_LINE);

                if (extended) {
                    for (int i=0; i <= m; i++) {
                        renderExtended(builder, causeStack[i], cache);
                    }
                } else {
                    for (int i=0; i <= m; i++) {
                        renderTrivial(builder, causeStack[i]);
                    }
                }
                if (framesInCommon != 0) {
                    builder.append("\t... ").append(framesInCommon).append(" more").append(NEW_LINE);
                }

                // Recurse if we have a cause
                final Throwable ourCause = cause.getCause();
                if (ourCause != null) {
                    renderCause(builder, cause, ourCause, cache, extended);
                }
            }
        };
    }

    static String getJarName(URL resource, String classResourceName) {
        if (resource == null) {
            return null;
        }

        final String path = resource.getPath();
        final String protocol = resource.getProtocol();

        if ("jar".equals(protocol)) {
            // the last path segment before "!/" should be the JAR name
            final int sepIdx = path.lastIndexOf("!/");
            if (sepIdx != -1) {
                // hit!
                final String firstPart = path.substring(0, sepIdx);
                // now find the last file separator before the JAR separator
                final int lsIdx = Math.max(firstPart.lastIndexOf('/'), firstPart.lastIndexOf('\\'));
                return firstPart.substring(lsIdx + 1);
            }
        } else if ("module".equals(protocol)) {
            return resource.getPath();
        }

        // OK, that would have been too easy.  Next let's just grab the last piece before the class name
        for (int endIdx = path.lastIndexOf(classResourceName); endIdx >= 0; endIdx--) {
            char ch = path.charAt(endIdx);
            if (ch == '/' || ch == '\\' || ch == '?') {
                String firstPart = path.substring(0, endIdx);
                int lsIdx = Math.max(firstPart.lastIndexOf('/'), firstPart.lastIndexOf('\\'));
                return firstPart.substring(lsIdx + 1);
            }
        }

        // OK, just use the last segment
        final int endIdx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(endIdx + 1);
    }

    /**
     * Create a format step which emits the log message resource key (if any) with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep resourceKeyFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return resourceKeyFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the log message resource key (if any) with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep resourceKeyFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                final String key = record.getResourceKey();
                if (key != null) builder.append(key);
            }
        };
    }

    /**
     * Create a format step which emits the source method name with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep methodNameFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return methodNameFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the source method name with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep methodNameFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getSourceMethodName());
            }
        };
    }

    private static final String separatorString;

    static {
        separatorString = doPrivileged(new PrivilegedAction<String>() {
            public String run() {
                return System.getProperty("line.separator");
            }
        });
    }

    /**
     * Create a format step which emits the platform line separator.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep lineSeparatorFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return lineSeparatorFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the platform line separator.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep lineSeparatorFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(separatorString);
            }
        };
    }

    /**
     * Create a format step which emits the log level name.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep levelFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return levelFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the log level name.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep levelFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                final Level level = record.getLevel();
                builder.append(level.getName());
            }
        };
    }

    /**
     * Create a format step which emits the localized log level name.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep localizedLevelFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return localizedLevelFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the localized log level name.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep localizedLevelFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                final Level level = record.getLevel();
                builder.append(level.getResourceBundleName() != null ? level.getLocalizedName() : level.getName());
            }
        };
    }

    /**
     * Create a format step which emits the number of milliseconds since the given base time.
     *
     * @param baseTime the base time as milliseconds as per {@link System#currentTimeMillis()}
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep relativeTimeFormatStep(final long baseTime, final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return relativeTimeFormatStep(baseTime, leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the number of milliseconds since the given base time.
     *
     * @param baseTime the base time as milliseconds as per {@link System#currentTimeMillis()}
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep relativeTimeFormatStep(final long baseTime, final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getMillis() - baseTime);
            }
        };
    }

    /**
     * Create a format step which emits the id if {@code id} is passed as the argument, otherwise the the thread name
     * is used.
     *
     * @param argument          the argument which may be {@code id} to indicate the thread id or {@code null} to
     *                          indicate the thread name
     * @param leftJustify       {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth      the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth      the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     *
     * @return the format step
     */
    public static FormatStep threadFormatStep(final String argument, final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        if (argument != null && THREAD_ID.equals(argument.toLowerCase(Locale.ROOT))) {
            return threadIdFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth);
        }
        return threadNameFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth);
    }

    /**
     * Create a format step which emits the id of the thread which originated the log record.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep threadIdFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getThreadID());
            }
        };
    }

    /**
     * Create a format step which emits the name of the thread which originated the log record.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep threadNameFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return threadNameFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the name of the thread which originated the log record.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep threadNameFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getThreadName());
            }
        };
    }

    /**
     * Create a format step which emits the NDC value of the log record.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep ndcFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return ndcFormatStep(leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth, 0);
    }

    /**
     * Create a format step which emits the NDC value of the log record.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param count the limit to the number of segments to format
     * @return the format step
     */
    public static FormatStep ndcFormatStep(final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth, final int count) {
        return new SegmentedFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth, count) {
            public String getSegmentedSubject(final ExtLogRecord record) {
                return record.getNdc();
            }
        };
    }

    /**
     * Create a format step which emits the MDC value associated with the given key of the log record.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep mdcFormatStep(final String key, final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return mdcFormatStep(key, leftJustify, minimumWidth, DEFAULT_TRUNCATE_BEGINNING, maximumWidth);
    }

    /**
     * Create a format step which emits the MDC value associated with the given key of the log record.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep mdcFormatStep(final String key, final boolean leftJustify, final int minimumWidth, final boolean truncateBeginning, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                final String value = record.getMdc(key);
                if (value != null) {
                    builder.append(value);
                }
            }
        };
    }

    public static FormatStep formatColor(final ColorMap colors, final String color) {
            return new FormatStep() {
            public void render(final StringBuilder builder, final ExtLogRecord record) {
                String code = colors.getCode(color, record.getLevel());
                if (code != null) {
                    builder.append(code);
                }
            }

            public int estimateLength() {
                return 7;
            }

        };
    }

    /**
     * Create a format step which emits a system property value associated with the given key.
     *
     * @param argument          the argument that may be a key or key with a default value separated by a colon, cannot
     *                          be {@code null}
     * @param leftJustify       {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth      the minimum field width, or 0 for none
     * @param truncateBeginning {@code true} to truncate the beginning, otherwise {@code false} to truncate the end
     * @param maximumWidth      the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     *
     * @return the format step
     *
     * @throws IllegalArgumentException if the {@code argument} is {@code null}
     * @throws SecurityException        if a security manager exists and its {@code checkPropertyAccess} method doesn't
     *                                  allow access to the specified system property
     */
    public static FormatStep systemPropertyFormatStep(final String argument, final boolean leftJustify, final int minimumWidth,
                                                      final boolean truncateBeginning, final int maximumWidth) {
        if (argument == null) {
            throw new IllegalArgumentException("System property requires a key for the lookup");
        }
        return new JustifyingFormatStep(leftJustify, minimumWidth, truncateBeginning, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                // Check for a default value
                final String[] parts = argument.split("(?<!\\\\):");
                final String key = parts[0];
                String value = System.getProperty(key);
                if (value == null && parts.length > 1) {
                    value = parts[1];
                }
                builder.append(value);
            }
        };
    }

    static Map<Integer, Segment> parsePatternSegments(final String pattern) {
        final Map<Integer, Segment> segments = new HashMap<Integer, Segment>();
        StringBuilder len = new StringBuilder();
        StringBuilder text = new StringBuilder();
        int pos = 0;
        // Process each character
        for (char c : pattern.toCharArray()) {
            if (c >= '0' && c <= '9') {
                len.append(c);
            } else if (c == '.') {
                pos++;
                final int i = (len.length() > 0 ? Integer.parseInt(len.toString()) : 0);
                segments.put(pos, new Segment(i, text.length() > 0 ? text.toString() : null));
                text = new StringBuilder();
                len = new StringBuilder();
            } else {
                text.append(c);
            }
        }
        if (len.length() > 0 || text.length() > 0) {
            pos++;
            final int i = (len.length() > 0 ? Integer.parseInt(len.toString()) : 0);
            segments.put(pos, new Segment(i, text.length() > 0 ? text.toString() : null));
        }
        return Collections.unmodifiableMap(segments);
    }

    static Deque<String> parseCategorySegments(final String category) {
        // The category needs to be split into segments
        final Deque<String> categorySegments = new ArrayDeque<String>();
        StringBuilder cat = new StringBuilder();
        for (char c : category.toCharArray()) {
            if (c == '.') {
                if (cat.length() > 0) {
                    categorySegments.add(cat.toString());
                    cat = new StringBuilder();
                } else {
                    categorySegments.add("");
                }
            } else {
                cat.append(c);
            }
        }
        if (cat.length() > 0) {
            categorySegments.add(cat.toString());
        }
        return categorySegments;
    }

    private static String findHostname(final Properties props, final Map<String, String> env, final boolean qualified) {
        if (qualified) {
            return findQualifiedHostname(props, env);
        }
        String hostname = props.getProperty("jboss.host.name");
        if (hostname == null) {
            final String qualifiedHostname = findQualifiedHostname(props, env);
            final int index = qualifiedHostname.indexOf('.');
            hostname = (index == -1 ? qualifiedHostname : qualifiedHostname.substring(0, index));
        }
        return hostname;
    }

    private static String findQualifiedHostname(final Properties props, final Map<String, String> env) {
        // First check the system property
        String qualifiedHostname = props.getProperty("jboss.qualified.host.name");
        if (qualifiedHostname == null) {
            qualifiedHostname = env.get("HOSTNAME");
            if (qualifiedHostname == null) {
                env.get("COMPUTERNAME");
            }
            if (qualifiedHostname == null) {
                try {
                    qualifiedHostname = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException ignore) {
                    qualifiedHostname = "unknown-host.unknown-domain";
                }
            }
        }
        return qualifiedHostname;
    }

    static class Segment {
        final int len;
        final String text;

        Segment(final int len, final String text) {
            this.len = len;
            this.text = text;
        }
    }
}
