/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.formatters;

import static java.lang.System.getSecurityManager;
import static java.lang.Thread.currentThread;
import static java.security.AccessController.doPrivileged;

import java.net.URL;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Formatter used to format the stack trace of an exception.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StackTraceFormatter {
    private static final String CAUSED_BY_CAPTION = "Caused by: ";
    private static final String SUPPRESSED_CAPTION = "Suppressed: ";
    // Used to guard against reentry when attempting to guess the class name
    private static final ThreadLocal<Boolean> ENTERED = new ThreadLocal<>();

    private final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    private final StringBuilder builder;
    private final int suppressedDepth;
    private final boolean extended;
    private final Map<String, String> cache;
    private int suppressedCount;

    private StackTraceFormatter(final StringBuilder builder, final int suppressedDepth, final boolean extended) {
        this.builder = builder;
        this.suppressedDepth = suppressedDepth;
        this.extended = extended;
        cache = extended ? new HashMap<String, String>() : null;
    }

    /**
     * Writes the stack trace into the builder.
     *
     * @param builder         the string builder ot append the stack trace to
     * @param t               the throwable to render
     * @param suppressedDepth the number of suppressed messages to include
     */
    public static void renderStackTrace(final StringBuilder builder, final Throwable t, final int suppressedDepth) {
        renderStackTrace(builder, t, false, suppressedDepth);
    }

    /**
     * Writes the stack trace into the builder.
     *
     * @param builder         the string builder ot append the stack trace to
     * @param t               the throwable to render
     * @param extended        {@code true} if the stack trace should attempt to resolve the JAR the class is in, otherwise
     *                        {@code false}
     * @param suppressedDepth the number of suppressed messages to include
     */
    static void renderStackTrace(final StringBuilder builder, final Throwable t, final boolean extended, final int suppressedDepth) {
        new StackTraceFormatter(builder, suppressedDepth, extended).renderStackTrace(t);
    }

    private void renderStackTrace(final Throwable t) {
        // Reset the suppression count
        suppressedCount = 0;
        // Write the exception message
        builder.append(": ").append(t);
        newLine();

        // Write the stack trace for this message
        final StackTraceElement[] stackTrace = t.getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (extended) {
                renderExtended("", element);
            } else {
                renderTrivial("", element);
            }
        }

        // Write any suppressed messages, if required
        if (suppressedDepth != 0) {
            for (Throwable se : t.getSuppressed()) {
                if (suppressedDepth < 0 || suppressedDepth > suppressedCount++) {
                    renderStackTrace(stackTrace, se, SUPPRESSED_CAPTION, "\t");
                }
            }
        }

        // Print cause if there is one
        final Throwable ourCause = t.getCause();
        if (ourCause != null) {
            renderStackTrace(stackTrace, ourCause, CAUSED_BY_CAPTION, "");
        }
    }

    private void renderStackTrace(final StackTraceElement[] parentStack, final Throwable child, final String caption, final String prefix) {
        if (seen.contains(child)) {
            builder.append("\t[CIRCULAR REFERENCE:")
                    .append(child)
                    .append(']');
            newLine();
        } else {
            seen.add(child);
            // Find the unique frames suppressing duplicates
            final StackTraceElement[] causeStack = child.getStackTrace();
            int m = causeStack.length - 1;
            int n = parentStack.length - 1;
            while (m >= 0 && n >= 0 && causeStack[m].equals(parentStack[n])) {
                m--;
                n--;
            }
            final int framesInCommon = causeStack.length - 1 - m;

            // Print our stack trace
            builder.append(prefix)
                    .append(caption)
                    .append(child);
            newLine();
            for (int i = 0; i <= m; i++) {
                if (extended) {
                    renderExtended(prefix, causeStack[i]);
                } else {
                    renderTrivial(prefix, causeStack[i]);
                }
            }
            if (framesInCommon != 0) {
                builder.append(prefix)
                        .append("\t... ")
                        .append(framesInCommon)
                        .append(" more");
                newLine();
            }

            // Print suppressed exceptions, if any
            if (suppressedDepth != 0) {
                for (Throwable se : child.getSuppressed()) {
                    if (suppressedDepth < 0 || suppressedDepth > suppressedCount++) {
                        renderStackTrace(causeStack, se, SUPPRESSED_CAPTION, prefix + "\t");
                    }
                }
            }

            // Print cause, if any
            Throwable ourCause = child.getCause();
            if (ourCause != null) {
                renderStackTrace(causeStack, ourCause, CAUSED_BY_CAPTION, prefix);
            }
        }
    }

    private void renderTrivial(final String prefix, final StackTraceElement element) {
        builder.append(prefix)
                .append("\tat ")
                .append(element);
        newLine();
    }

    private void renderExtended(final String prefix, final StackTraceElement element) {
        builder.append(prefix)
                .append("\tat ")
                .append(element);
        final String className = element.getClassName();
        final String cached;
        if ((cached = cache.get(className)) != null) {
            builder.append(cached);
            newLine();
            return;
        }
        final int dotIdx = className.lastIndexOf('.');
        if (dotIdx == -1) {
            newLine();
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
        final SecurityManager sm = getSecurityManager();
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
                    resource = doPrivileged(new PrivilegedAction<URL>() {
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
                    resource = doPrivileged(new PrivilegedAction<URL>() {
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
        final String jarName = getJarName(resource, classResourceName);

        // finally, render the mess
        boolean started = false;
        final StringBuilder tagBuilder = new StringBuilder();
        if (jarName != null) {
            started = true;
            tagBuilder.append(" [").append(jarName).append(':');
        }
        if (packageVersion != null) {
            if (!started) {
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
        newLine();
    }

    private void newLine() {
        builder.append(System.lineSeparator());
    }

    private static Class<?> guessClass(final String name) {
        if (ENTERED.get() != null) return null;
        ENTERED.set(Boolean.TRUE);
        try {
            try {
                final ClassLoader tccl = currentThread().getContextClassLoader();
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
        } finally {
            ENTERED.remove();
        }
    }

    /**
     * Attempts to parse the JAR name from the resource URL.
     *
     * @param resource          the URL for the resource
     * @param classResourceName the name of the name of the class within the resource
     *
     * @return the name of the JAR or module
     */
    static String getJarName(URL resource, String classResourceName) {
        if (resource == null) {
            return null;
        }

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
                return firstPart.substring(lsIdx + 1);
            }
        } else if ("module".equals(protocol)) {
            return resource.getPath();
        }

        // OK, that would have been too easy.  Next let's just grab the last piece before the class name
        for (int endIdx = path.lastIndexOf(classResourceName); endIdx >= 0; endIdx--) {
            char ch = path.charAt(endIdx);
            if (ch == '/' || ch == '\\' || ch == '?') {
                String firstPart = path.substring(0, endIdx);
                int lsIdx = Math.max(firstPart.lastIndexOf('/'), firstPart.lastIndexOf('\\'));
                return firstPart.substring(lsIdx + 1);
            }
        }

        // OK, just use the last segment
        final int endIdx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(endIdx + 1);
    }
}
