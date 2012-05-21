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

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.Level;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import static java.lang.Math.min;
import static java.lang.Math.max;
import java.io.PrintWriter;
import org.jboss.logmanager.NDC;

/**
 * Formatter utility methods.
 */
public final class Formatters {

    private static final String NEW_LINE = String.format("%n");

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
                final int writtenLen = newLen - oldLen;
                final int overflow = writtenLen - maximumWidth;
                if (overflow > 0) {
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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
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
     * Create a format step which emits the formatted log message text (simple version, no exception traces) with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep simpleMessageFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
                builder.append(record.getFormattedMessage());
            }
        };
    }

    private static final PrivilegedAction<ClassLoader> GET_TCCL_ACTION = new PrivilegedAction<ClassLoader>() {
        public ClassLoader run() {
            return Thread.currentThread().getContextClassLoader();
        }
    };

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
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
            public void renderRaw(final StringBuilder builder, final ExtLogRecord record) {
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
                final SecurityManager sm = System.getSecurityManager();
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
                            resource = AccessController.doPrivileged(new PrivilegedAction<URL>() {
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
                            resource = AccessController.doPrivileged(new PrivilegedAction<URL>() {
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
                String jarName = null;
                if (resource != null) {
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
                            if (lsIdx != -1) {
                                jarName = firstPart.substring(lsIdx + 1);
                            } else {
                                jarName = firstPart;
                            }
                        }
                    } else if ("module".equals(protocol)) {
                        jarName = resource.getPath();
                    }
                    if (jarName == null) {
                        // OK, that would have been too easy.  Next let's just grab the last piece before the class name
                        int endIdx = path.lastIndexOf(classResourceName);
                        if (endIdx > 0) {
                            do {
                                endIdx--;
                            } while (path.charAt(endIdx) == '/' || path.charAt(endIdx) == '\\' || path.charAt(endIdx) == '?');
                            final String firstPart = path.substring(0, endIdx);
                            final int lsIdx = Math.max(firstPart.lastIndexOf('/'), firstPart.lastIndexOf('\\'));
                            if (lsIdx != -1) {
                                jarName = firstPart.substring(lsIdx + 1);
                            } else {
                                jarName = firstPart;
                            }
                        }
                    }
                    if (jarName == null) {
                        // OK, just use the last segment
                        final int endIdx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                        if (endIdx != -1) {
                            jarName = path.substring(endIdx + 1);
                        } else {
                            jarName = path;
                        }
                    }
                    if (jarName == null && exceptionClass != null && exceptionClass.getClassLoader() == null) {
                        jarName = "(bootstrap)";
                    }
                }

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
                final SecurityManager sm = System.getSecurityManager();
                try {
                    try {
                        final ClassLoader tccl = sm != null ? AccessController.doPrivileged(GET_TCCL_ACTION) : GET_TCCL_ACTION.run();
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

    /**
     * Create a format step which emits the log message resource key (if any) with the given justification rules.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @return the format step
     */
    public static FormatStep resourceKeyFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth) {
        return new JustifyingFormatStep(leftJustify, minimumWidth, maximumWidth) {
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
     * Create a format step which emits the log level name.
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
        return ndcFormatStep(leftJustify, minimumWidth, maximumWidth, 0);
    }

    /**
     * Create a format step which emits the NDC value of the log record.
     *
     * @param leftJustify {@code true} to left justify, {@code false} to right justify
     * @param minimumWidth the minimum field width, or 0 for none
     * @param maximumWidth the maximum field width (must be greater than {@code minimumFieldWidth}), or 0 for none
     * @param count the limit to the number of segments to format
     * @return the format step
     */
    public static FormatStep ndcFormatStep(final boolean leftJustify, final int minimumWidth, final int maximumWidth, final int count) {
        return new SegmentedFormatStep(leftJustify, minimumWidth, maximumWidth, count) {
            public String getSegmentedSubject(final ExtLogRecord record) {
                return NDC.get();
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
