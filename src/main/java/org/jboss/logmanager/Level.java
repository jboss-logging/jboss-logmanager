package org.jboss.logmanager;

/**
 * Log4j-like levels.
 */
public final class Level extends java.util.logging.Level {
    private static final long serialVersionUID = 491981186783136939L;

    protected Level(final String name, final int value) {
        super(name, value);
    }

    protected Level(final String name, final int value, final String resourceBundleName) {
        super(name, value, resourceBundleName);
    }

    public static final Level FATAL = new Level("FATAL", 1100);
    public static final Level ERROR = new Level("ERROR", 1000);
    public static final Level WARN = new Level("WARN", 900);
    public static final Level INFO = new Level("INFO", 800);
    public static final Level DEBUG = new Level("DEBUG", 500);
    public static final Level TRACE = new Level("TRACE", 400);
}
