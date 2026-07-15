package org.jboss.logmanager.formatters;

import java.io.IOException;

/**
 * The default implementation of {@link StackTraceFormatter}.
 */
final class StackTraceFormatterImpl implements StackTraceFormatter {
    /**
     * Construct a new instance.
     */
    private StackTraceFormatterImpl() {
    }

    static final StackTraceFormatterImpl INSTANCE = new StackTraceFormatterImpl();

    public void render(final Throwable t, final Appendable output, final Parameters parameters) throws IOException {
        StringBuilder sb;
        if (output instanceof StringBuilder) {
            sb = (StringBuilder) output;
            BasicStackTraceFormatter.renderStackTrace(sb, t, parameters.suppressedDepth());
        } else {
            sb = new StringBuilder();
            BasicStackTraceFormatter.renderStackTrace(sb, t, parameters.suppressedDepth());
            output.append(sb);
        }
    }
}
