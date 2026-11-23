package org.jboss.logmanager.formatters;

import java.io.IOException;
import java.util.ServiceLoader;

/**
 * A stack trace formatter.
 * An implementation of this interface is located using {@link ServiceLoader} to determine how to format
 * stack traces.
 */
public interface StackTraceFormatter {
    /**
     * Render an exception stack trace to the given output.
     *
     * @param t          the root exception (not {@code null})
     * @param output     the output to which the exception stack trace should be written (not {@code null})
     * @param parameters the format parameters (not {@code null})
     */
    void render(Throwable t, Appendable output, Parameters parameters) throws IOException;

    /**
     * Parameters for a stack trace formatting request.
     */
    interface Parameters {
        /**
         * {@return <code>true</code> if the "extended" hint is given, or <code>false</code> otherwise}
         * Implementations may ignore this parameter; it is only a hint.
         */
        default boolean extended() {
            return false;
        }

        /**
         * {@return the maximum depth of nested or suppressed exceptions to render}
         * Implementations may ignore this parameter; it is only a hint.
         */
        default int suppressedDepth() {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Get the singleton stack trace formatter instance.
     *
     * @return the stack trace formatter instance (not {@code null})
     */
    static StackTraceFormatter instance() {
        return StackTraceFormatterHolder.INSTANCE;
    }
}
