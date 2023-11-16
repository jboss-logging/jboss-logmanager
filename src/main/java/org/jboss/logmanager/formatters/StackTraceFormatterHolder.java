package org.jboss.logmanager.formatters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 *
 */
final class StackTraceFormatterHolder {
    private StackTraceFormatterHolder() {
    }

    static final StackTraceFormatter INSTANCE;

    static {
        StackTraceFormatter instance = null;
        // we can't use service loader here, because of course it logs, causing things to fail weirdly.
        // so, do our own thing
        try {
            ClassLoader cl = StackTraceFormatterHolder.class.getClassLoader();
            Enumeration<URL> e = cl.getResources("META-INF/services/" + StackTraceFormatter.class.getName());
            out: while (e.hasMoreElements()) {
                try {
                    URL url = e.nextElement();
                    try (InputStream is = url.openStream();
                            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                            BufferedReader br = new BufferedReader(isr)) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            int idx = line.indexOf('#');
                            if (idx != -1) {
                                line = line.substring(idx);
                            }
                            line = line.trim();
                            if (!line.isBlank()) {
                                try {
                                    Class<? extends StackTraceFormatter> stfClass = Class.forName(line, false, cl)
                                            .asSubclass(StackTraceFormatter.class);
                                    MethodHandle ctor = MethodHandles.publicLookup().findConstructor(stfClass,
                                            MethodType.methodType(void.class));
                                    instance = (StackTraceFormatter) ctor.invoke();
                                    break out;
                                } catch (Throwable ignored) {
                                    // just try the next line
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // try the next file
                }
            }
        } catch (Throwable ignored) {
            // can't get the resources at all; give up
        }
        if (instance == null) {
            instance = StackTraceFormatterImpl.INSTANCE;
        }
        INSTANCE = instance;
    }
}
