/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
 *
 */
public final class NDC {

    private NDC() {}

    private static final Holder ndc = new Holder();

    public static int push(String context) {
        final Stack<String> stack = ndc.get();
        try {
            return stack.depth();
        } finally {
            stack.push(context);
        }
    }

    public static String pop() {
        final Stack<String> stack = ndc.get();
        if (stack.isEmpty()) {
            return "";
        } else {
            return stack.pop();
        }
    }

    public static void clear() {
        ndc.get().trimTo(0);
    }

    public static void trimTo(int size) {
        ndc.get().trimTo(size);
    }

    public static String get() {
        final Stack<String> stack = ndc.get();
        if (stack.isEmpty()) {
            return "";
        } else {
            return stack.top();
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
    }
}
