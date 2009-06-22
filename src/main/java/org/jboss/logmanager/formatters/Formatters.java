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

package org.jboss.logmanager.formatters;

import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.Level;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.Date;
import static java.lang.Math.min;
import static java.lang.Math.max;
import java.io.PrintWriter;

/**
 * Formatter utility methods.
 */
public final class Formatters {

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

    private static abstract class JustifyingFormatStep implements FormatStep {
        private final boolean leftJustify;
        private final int minimumWidth;
        private final int maximumWidth;

        protected JustifyingFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
            if (maximumWidth != 0 && minimumWidth > maximumWidth) {
                throw new IllegalArgumentException("Specified minimum width may not be greater than the specified maximum width");
            }
            if (maximumWidth < 0 || minimumWidth < 0) {
                throw new IllegalArgumentException("Minimum and maximum widths must not be less than zero");
            }
            this.leftJustify = leftJustify;
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
                final int overflow = (newLen - oldLen) - maximumWidth;
                if (overflow > 0) {
                    builder.setLength(newLen - overflow);
                } else {
                    final int spaces = minimumWidth - (newLen - oldLen);
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
            if (maximumWidth != 0) {
                return min(maximumWidth, minimumWidth * 3);
            } else {
                return max(32, minimumWidth);
            }
        }

        public abstract void renderRaw(final StringBuilder builder, final ExtLogRecord record);
    }

    private static abstract class SegmentedFormatStep extends JustifyingFormatStep {
        private final int count;

        protected SegmentedFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth, final int count) {
            super(leftJustify, minimumWidth, maximumWidth);
            this.count = count;
        }

        public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
            builder.append(applySegments(count, getSegmentedSubject(record)));
        }

        public abstract String getSegmentedSubject(final ExtLogRecord record);
    }

    /**
     * Create a format step which emits the logger name with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param count the maximum number of logger name segments to emit (counting from the right)
     * @return the format step
     */
    public static FormatStep loggerNameFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth, final int count) {
        return new SegmentedFormatStep(leftJustify, minimumWidth, maximumWidth, count) {
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
     * @param count the maximum number of class name segments to emit (counting from the right)
     * @return the format step
     */
    public static FormatStep classNameFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth, final int count) {
        return new SegmentedFormatStep(leftJustify, minimumWidth, maximumWidth, count) {
            public String getSegmentedSubject(final ExtLogRecord record) {
                return record.getSourceClassName();
            }
        };
    }

    /**
     * Create a format step which emits the date of the log record with the given justificaiton rules.
     *
     * @param formatString the date format string
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep dateFormatStep(final String formatString, final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        final SimpleDateFormat dateFormatMaster = new SimpleDateFormat(formatString);
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                final SimpleDateFormat dateFormat = dateFormatMaster;
                synchronized (dateFormat) {
                    builder.append(dateFormat.format(new Date(record.getMillis())));
                }
            }
        };
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getSourceFileName());
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
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
     * Create a format step which emits the source method name with the given justification rules (NOTE: call stack
     * introspection introduces a significant performance penalty).
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep methodNameFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getSourceMethodName());
            }
        };
    }

    private static final String separatorString;

    static {
        separatorString = AccessController.doPrivileged(new PrivilegedAction<String>() {
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(separatorString);
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
    public static FormatStep levelFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getMillis() - baseTime);
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                final String value = record.getNdc();
                if (value != null) {
                    builder.append(value);
                }
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                final String value = record.getMdc(key);
                if (value != null) {
                    builder.append(value);
                }
            }
        };
    }
}
