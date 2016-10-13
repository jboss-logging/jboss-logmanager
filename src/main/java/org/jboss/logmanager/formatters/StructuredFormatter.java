/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;

/**
 * An abstract class that uses a generator to help generate structured data from a {@link
 * org.jboss.logmanager.ExtLogRecord record}.
 * <p/>
 * Note that including details can be expensive in terms of calculating the caller.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class StructuredFormatter extends ExtFormatter {

    /**
     * The keys used for the structured log record data.
     */
    public static interface Keys {

        String EXCEPTION = "exception";

        String EXCEPTION_FRAME = "frame";

        String EXCEPTION_FRAME_CLASS = "class";

        String EXCEPTION_FRAME_LINE = "line";

        String EXCEPTION_FRAME_METHOD = "method";

        String EXCEPTION_FRAMES = "frames";

        String EXCEPTION_MESSAGE = "message";

        String FORMAT_STYLE_KEY = "formatStyle";

        String FORMATTED_MESSAGE_KEY = "formattedMessage";

        String LEVEL_KEY = "level";

        String LOGGER_CLASS_NAME_KEY = "loggerClassName";

        String LOGGER_NAME_KEY = "loggerName";

        String MDC_KEY = "mdc";

        String MESSAGE_KEY = "message";

        String NDC_KEY = "ndc";

        String PARAMETER_KEY = "parameter";

        String PARAMETERS_KEY = "parameters";

        String RECORD_KEY = "record";

        String SEQUENCE_KEY = "sequence";

        String SOURCE_CLASS_NAME_KEY = "sourceClassName";

        String SOURCE_FILE_NAME_KEY = "sourceFileName";

        String SOURCE_LINE_NUMBER_KEY = "sourceLineNumber";

        String SOURCE_METHOD_NAME_KEY = "sourceMethodName";

        String THREAD_ID_KEY = "threadId";

        String THREAD_NAME_KEY = "threadName";

        String TIMESTAMP_KEY = "timestamp";
    }

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private final ThreadLocal<SimpleDateFormat> dateFormatThreadLocal = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            final String dateFormat = StructuredFormatter.this.pattern;
            return new SimpleDateFormat(dateFormat == null ? DEFAULT_DATE_FORMAT : dateFormat);
        }
    };

    private volatile boolean printDetails;
    private volatile String pattern;

    protected StructuredFormatter(final boolean printDetails) {
        this.printDetails = printDetails;
        pattern = DEFAULT_DATE_FORMAT;
    }

    /**
     * Creates the generator used to create the structured data.
     *
     * @return the generator to use
     *
     * @throws Exception if an error occurs creating the generator
     */
    protected abstract Generator createGenerator() throws Exception;

    @Override
    public String format(final ExtLogRecord record) {
        final boolean details = printDetails;
        try {
            // TODO (jrp) this might not be very efficient
            final SimpleDateFormat sdf = dateFormatThreadLocal.get();
            final String pattern = this.pattern;
            if (!sdf.toPattern().equals(pattern)) {
                sdf.applyPattern(pattern);
            }
            final Generator generator = createGenerator().begin();
            generator.add(Keys.TIMESTAMP_KEY, dateFormatThreadLocal.get().format(new Date(record.getMillis())))
                    .add(Keys.SEQUENCE_KEY, record.getSequenceNumber())
                    .add(Keys.LOGGER_CLASS_NAME_KEY, record.getLoggerClassName())
                    .add(Keys.LOGGER_NAME_KEY, record.getLoggerName())
                    .add(Keys.LEVEL_KEY, record.getLevel().getName())
                    .add(Keys.THREAD_NAME_KEY, record.getThreadName())
                    .add(Keys.MESSAGE_KEY, record.getMessage())
                    .add(Keys.THREAD_ID_KEY, record.getThreadID())
                    .add(Keys.FORMATTED_MESSAGE_KEY, record.getFormattedMessage())
                    .add(Keys.FORMAT_STYLE_KEY, record.getFormatStyle())
                    .add(Keys.PARAMETERS_KEY, Keys.PARAMETER_KEY, record.getParameters())
                    .add(Keys.MDC_KEY, record.getMdcCopy())
                    .add(Keys.NDC_KEY, record.getNdc());
            final Throwable t = record.getThrown();
            if (t != null) {
                generator.addStackTrace(t);
            }
            if (details) {
                generator.add(Keys.SOURCE_CLASS_NAME_KEY, record.getSourceClassName())
                        .add(Keys.SOURCE_FILE_NAME_KEY, record.getSourceFileName())
                        .add(Keys.SOURCE_METHOD_NAME_KEY, record.getSourceMethodName())
                        .add(Keys.SOURCE_LINE_NUMBER_KEY, record.getSourceLineNumber());
            }
            return generator.build();
        } catch (Exception e) {
            // Wrap and rethrow
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the current date format.
     *
     * @return the current date format
     */
    public String getDateFormat() {
        return pattern;
    }

    /**
     * Sets the pattern to use when formatting the date. The pattern must be a valid {@link java.text.SimpleDateFormat}
     * pattern.
     * <p/>
     * If the pattern is {@code null} a default pattern will be used.
     *
     * @param pattern the pattern to use
     */
    public void setDateFormat(final String pattern) {
        if (pattern == null) {
            this.pattern = DEFAULT_DATE_FORMAT;
        } else {
            this.pattern = pattern;
        }
    }

    /**
     * Indicates whether or not details should be printed.
     *
     * @return {@code true} if details should be printed, otherwise {@code false}
     */
    public boolean isPrintDetails() {
        return printDetails;
    }

    /**
     * Sets whether or not details should be printed.
     * <p/>
     * Printing the details can be expensive as the values are retrieved from the caller. The details include the
     * source class name, source file name, source method name and source line number.
     *
     * @param printDetails {@code true} if details should be printed
     */
    public void setPrintDetails(final boolean printDetails) {
        this.printDetails = printDetails;
    }

    /**
     * A generator used to create the structured output.
     */
    protected static abstract class Generator {

        /**
         * Initial method invoked at the start of the generation.
         *
         * @return the generator
         *
         * @throws Exception if an error occurs while adding the data
         */
        public Generator begin() throws Exception {
            return this;
        }

        /**
         * Writes an integer value.
         *
         * @param key   they key
         * @param value the value
         *
         * @return the generator
         *
         * @throws Exception if an error occurs while adding the data
         */
        public Generator add(final String key, final int value) throws Exception {
            add(key, Integer.toString(value));
            return this;
        }

        /**
         * Writes a long value.
         *
         * @param key   they key
         * @param value the value
         *
         * @return the generator
         *
         * @throws Exception if an error occurs while adding the data
         */
        public Generator add(final String key, final long value) throws Exception {
            add(key, Long.toString(value));
            return this;
        }

        /**
         * Writes an num value.
         * <p/>
         * Uses the {@link Enum#name() name} of the enum as the value written.
         *
         * @param key   they key
         * @param value the value
         *
         * @return the generator
         *
         * @throws Exception if an error occurs while adding the data
         */
        public Generator add(final String key, final Enum<?> value) throws Exception {
            if (value == null) {
                add(key, (String) null);
            } else {
                add(key, value.name());
            }
            return this;
        }

        /**
         * Writes an array value.
         *
         * @param key      the key for the array
         * @param valueKey the key for the value if required
         * @param value    the array of values
         *
         * @return the generator
         *
         * @throws Exception if an error occurs while adding the data
         */
        public abstract Generator add(String key, String valueKey, Object[] value) throws Exception;

        /**
         * Writes a map value
         *
         * @param key   the key for the map
         * @param value the map
         *
         * @return the generator
         *
         * @throws Exception if an error occurs while adding the data
         */
        public abstract Generator add(String key, Map<String, ?> value) throws Exception;

        /**
         * Writes a string value.
         *
         * @param key   the key for the value
         * @param value the string value
         *
         * @return the generator
         *
         * @throws Exception if an error occurs while adding the data
         */
        public abstract Generator add(String key, String value) throws Exception;

        /**
         * Writes the stack trace.
         * <p/>
         * The implementation is allowed to write the stack trace however is desirable. For example the frames of the
         * stack trace can be broken down into an array or {@link Throwable#printStackTrace()} could be used to
         * represent the stack trace.
         *
         * @param throwable the exception to write the stack trace for
         *
         * @return the generator
         *
         * @throws Exception if an error occurs while adding the data
         */
        public abstract Generator addStackTrace(Throwable throwable) throws Exception;

        /**
         * Creates the structured data string.
         *
         * @return the formatted string
         *
         * @throws Exception if an error occurs while adding the data during the build
         */
        public abstract String build() throws Exception;
    }
}
