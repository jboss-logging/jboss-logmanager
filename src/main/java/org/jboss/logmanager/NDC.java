/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Nested diagnostic context. This is basically a thread-local stack that holds a string which can be included
 * in a log message.
 */
public final class NDC {
    private static final NDCProvider ndcProvider = getDefaultNDCProvider();

    private NDC() {
    }

    static NDCProvider getNDCProvider() {
        return ndcProvider;
    }

    static NDCProvider getDefaultNDCProvider() {
        return System.getSecurityManager() == null ? doGetDefaultNDCProvider()
                : AccessController.doPrivileged((PrivilegedAction<NDCProvider>) NDC::doGetDefaultNDCProvider);
    }

    static NDCProvider doGetDefaultNDCProvider() {
        final ServiceLoader<NDCProvider> configLoader = ServiceLoader.load(NDCProvider.class, NDC.class.getClassLoader());
        final Iterator<NDCProvider> iterator = configLoader.iterator();
        for (;;)
            try {
                if (!iterator.hasNext()) {
                    return new ThreadLocalNDC();
                }
                return iterator.next();
            } catch (ServiceConfigurationError | RuntimeException e) {
                System.err.print("Warning: failed to load NDC Provider: ");
                e.printStackTrace(System.err);
            }
    }

    /**
     * Push a value on to the NDC stack, returning the new stack depth which should later be used to restore the stack.
     *
     * @param context the new value
     * @return the new stack depth
     */
    public static int push(String context) {
        return ndcProvider.push(context);
    }

    /**
     * Pop the topmost value from the NDC stack and return it.
     *
     * @return the old topmost value
     */
    public static String pop() {
        return ndcProvider.pop();
    }

    /**
     * Clear the thread's NDC stack.
     */
    public static void clear() {
        ndcProvider.clear();
    }

    /**
     * Trim the thread NDC stack down to no larger than the given size. Used to restore the stack to the depth returned
     * by a {@code push()}.
     *
     * @param size the new size
     */
    public static void trimTo(int size) {
        ndcProvider.trimTo(size);
    }

    /**
     * Get the current NDC stack depth.
     *
     * @return the stack depth
     */
    public static int getDepth() {
        return ndcProvider.getDepth();
    }

    /**
     * Get the current NDC value.
     *
     * @return the current NDC value, or {@code ""} if there is none
     */
    public static String get() {
        return ndcProvider.get();
    }

    /**
     * Provided for compatibility with log4j. Get the NDC value that is {@code n} entries from the bottom.
     *
     * @param n the index
     * @return the value or {@code null} if there is none
     */
    public static String get(int n) {
        return ndcProvider.get(n);
    }
}
