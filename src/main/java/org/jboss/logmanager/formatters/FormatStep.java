package org.jboss.logmanager.formatters;

import java.util.logging.Formatter;

import org.jboss.logmanager.ExtLogRecord;

/**
 * A single format step which handles some part of rendering a log record.
 */
public interface FormatStep {

    /**
     * Render a part of the log record.
     *
     * @param builder the string builder to append to
     * @param record  the record being rendered
     */
    void render(StringBuilder builder, ExtLogRecord record);

    /**
     * Render a part of the log record to the given formatter.
     *
     * @param formatter the formatter to render to
     * @param builder   the string builder to append to
     * @param record    the record being rendered
     */
    default void render(Formatter formatter, StringBuilder builder, ExtLogRecord record) {
        render(builder, record);
    }

    /**
     * Emit an estimate of the length of data which this step will produce. The more accurate the estimate, the
     * more likely the format operation will be performant.
     *
     * @return an estimate
     */
    int estimateLength();

    /**
     * Indicates whether or not caller information is required for this format step.
     *
     * @return {@code true} if caller information is required, otherwise {@code false}
     */
    default boolean isCallerInformationRequired() {
        return false;
    }

    /**
     * Get the item type of this step.
     *
     * @return the item type
     */
    default ItemType getItemType() {
        return ItemType.GENERIC;
    }

    /**
     * An enumeration of the types of items that can be rendered. Note that this enumeration may be expanded
     * in the future, so unknown values should be handled gracefully as if {@link #GENERIC} were used.
     */
    enum ItemType {
        /** An item of unknown kind. */
        GENERIC,

        /** A compound step. */
        COMPOUND,

        // == // == //

        /** A log level. */
        LEVEL,

        // == // == //

        SOURCE_CLASS_NAME,
        DATE,
        SOURCE_FILE_NAME,
        HOST_NAME,
        SOURCE_LINE_NUMBER,
        LINE_SEPARATOR,
        CATEGORY,
        MDC,
        /**
         * The log message without the exception trace.
         */
        MESSAGE,
        EXCEPTION_TRACE,
        SOURCE_METHOD_NAME,
        SOURCE_MODULE_NAME,
        SOURCE_MODULE_VERSION,
        NDC,
        PROCESS_ID,
        PROCESS_NAME,
        RELATIVE_TIME,
        RESOURCE_KEY,
        SYSTEM_PROPERTY,
        TEXT,
        THREAD_ID,
        THREAD_NAME,
    }
}
