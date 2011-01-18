/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.logmanager;

import java.util.Arrays;

/**
 * Nested diagnostic context.  This is basically a thread-local stack that holds a string which can be included
 * in a log message.
 */
public final class NDC {

    private NDC() {}

    private static final Holder ndc = new Holder();

    /**
     * Push a value on to the NDC stack, returning the new stack depth which should later be used to restore the stack.
     *
     * @param context the new value
     * @return the new stack depth
     */
    public static int push(String context) {
        final Stack<String> stack = ndc.get();
        try {
            return stack.depth();
        } finally {
            stack.push(context);
        }
    }

    /**
     * Pop the topmost value from the NDC stack and return it.
     *
     * @return the old topmost value
     */
    public static String pop() {
        final Stack<String> stack = ndc.get();
        if (stack.isEmpty()) {
            return "";
        } else {
            return stack.pop();
        }
    }

    /**
     * Clear the thread's NDC stack.
     */
    public static void clear() {
        ndc.get().trimTo(0);
    }

    /**
     * Trim the thread NDC stack down to no larger than the given size.  Used to restore the stack to the depth returned
     * by a {@code push()}.
     *
     * @param size the new size
     */
    public static void trimTo(int size) {
        ndc.get().trimTo(size);
    }

    /**
     * Get the current NDC stack depth.
     *
     * @return the stack depth
     */
    public static int getDepth() {
        return ndc.get().depth();
    }

    /**
     * Get the current NDC value.
     *
     * @return the current NDC value, or {@code ""} if there is none
     */
    public static String get() {
        final Stack<String> stack = ndc.get();
        if (stack.isEmpty()) {
            return "";
        } else {
            return stack.toString();
        }
    }

    private static final class Holder extends ThreadLocal<Stack<String>> {
        protected Stack<String> initialValue() {
            return new Stack<String>();
        }
    }

    private static final class Stack<T> {
        private Object[] data = new Object[32];
        private int sp;

        public void push(T value) {
            final int oldlen = data.length;
            if (sp == oldlen) {
                Object[] newdata = new Object[oldlen * 3 / 2];
                System.arraycopy(data, 0, newdata, 0, oldlen);
                data = newdata;
            }
            data[sp++] = value;
        }

        @SuppressWarnings({ "unchecked" })
        public T pop() {
            try {
                return (T) data[--sp];
            } finally {
                data[sp] = null;
            }
        }

        @SuppressWarnings({ "unchecked" })
        public T top() {
            return (T) data[sp - 1];
        }

        public boolean isEmpty() {
            return sp == 0;
        }

        public int depth() {
            return sp;
        }

        public void trimTo(int max) {
            final int sp = this.sp;
            if (sp > max) {
                Arrays.fill(data, max, sp - 1, null);
                this.sp = max;
            }
        }

        public String toString() {
            final StringBuilder b = new StringBuilder();
            final int sp = this.sp;
            for (int i = 0; i < sp; i++) {
                b.append(data[i]);
                if (i < sp) {
                    b.append('.');
                }
            }
            return b.toString();
        }
    }
}
