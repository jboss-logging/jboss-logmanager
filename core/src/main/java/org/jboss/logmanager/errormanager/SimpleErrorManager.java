package org.jboss.logmanager.errormanager;

import org.jboss.logmanager.ExtErrorManager;
import org.jboss.logmanager.StandardOutputStreams;

/**
 * An error manager which simply prints a message the system error stream.
 */
public class SimpleErrorManager extends ExtErrorManager {
    public void error(final String msg, final Exception ex, final int code) {
        StandardOutputStreams.printError(ex, "LogManager error of type %s: %s%n", nameForCode(code), msg);
    }
}
