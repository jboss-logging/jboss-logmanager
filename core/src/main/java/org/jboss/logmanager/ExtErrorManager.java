package org.jboss.logmanager;

import java.util.logging.ErrorManager;

/**
 * An extended error manager, which contains additional useful utilities for error managers.
 */
public abstract class ExtErrorManager extends ErrorManager {

    /**
     * Get the name corresponding to the given error code.
     *
     * @param code the error code
     * @return the corresponding name (not {@code null})
     */
    protected String nameForCode(int code) {
        switch (code) {
            case CLOSE_FAILURE: return "CLOSE_FAILURE";
            case FLUSH_FAILURE: return "FLUSH_FAILURE";
            case FORMAT_FAILURE: return "FORMAT_FAILURE";
            case GENERIC_FAILURE: return "GENERIC_FAILURE";
            case OPEN_FAILURE: return "OPEN_FAILURE";
            case WRITE_FAILURE: return "WRITE_FAILURE";
            default: return "INVALID (" + code + ")";
        }
    }

    public void error(final String msg, final Exception ex, final int code) {
        super.error(msg, ex, code);
    }

    /**
     * Convert the given error to a log record which can be published to handler(s) or stored.  Care should
     * be taken not to publish the log record to a logger that writes to the same handler that produced the error.
     *
     * @param msg the error message (possibly {@code null})
     * @param ex the error exception (possibly {@code null})
     * @param code the error code
     * @return the log record (not {@code null})
     */
    protected ExtLogRecord errorToLogRecord(String msg, Exception ex, int code) {
        final ExtLogRecord record = new ExtLogRecord(Level.ERROR, "Failed to publish log record (%s[%d]): %s", ExtLogRecord.FormatStyle.PRINTF, getClass().getName());
        final String codeStr = nameForCode(code);
        record.setParameters(new Object[] {
            codeStr,
            Integer.toString(code),
            msg,
        });
        record.setThrown(ex);
        return record;
    }
}
