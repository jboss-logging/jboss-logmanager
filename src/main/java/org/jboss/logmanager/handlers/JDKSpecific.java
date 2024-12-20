package org.jboss.logmanager.handlers;

import java.io.Console;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * JDK-specific code relating to {@link Console}.
 */
final class JDKSpecific {
    private JDKSpecific() {
    }

    private static final Charset CONSOLE_CHARSET;

    static {
        Console console = System.console();
        Charset charset;
        if (console != null) {
            charset = console.charset();
        } else {
            // Make various guesses as to what the encoding of the console is
            String encodingName = AccessController
                    .doPrivileged((PrivilegedAction<String>) () -> System.getProperty("stdout.encoding"));
            if (encodingName == null) {
                encodingName = AccessController
                        .doPrivileged((PrivilegedAction<String>) () -> System.getProperty("native.encoding"));
            }
            charset = encodingName == null ? Charset.defaultCharset() : Charset.forName(encodingName);
        }
        CONSOLE_CHARSET = charset;
    }

    static Charset consoleCharset() {
        return CONSOLE_CHARSET;
    }
}
