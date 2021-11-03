package org.jboss.logmanager;

import java.util.Arrays;

final class ThreadLocalNDC implements NDCProvider {
    private static final Holder ndc = new Holder();

    @Override
    public int push(String context) {
        final Stack<String> stack = ndc.get();
        try {
            return stack.depth();
        } finally {
            stack.push(context);
        }
    }

    @Override
    public String pop() {
        final Stack<String> stack = ndc.get();
        if (stack.isEmpty()) {
            return "";
        } else {
            return stack.pop();
        }
    }

    @Override
    public void clear() {
        ndc.get().trimTo(0);
    }

    @Override
    public void trimTo(int size) {
        ndc.get().trimTo(size);
    }

    @Override
    public int getDepth() {
        return ndc.get().depth();
    }

    @Override
    public String get() {
        final Stack<String> stack = ndc.get();
        if (stack.isEmpty()) {
            return "";
        } else {
            return stack.toString();
        }
    }

    @Override
    public String get(int n) {
        return ndc.get().get(n);
    }

    private static final class Holder extends ThreadLocal<Stack<String>> {
        protected Stack<String> initialValue() {
            return new Stack<>();
        }
    }

    private static final class Stack<T> {
        private Object[] data = new Object[32];
        private int sp;

        public void push(T value) {
            if (sp == data.length) {
                data = Arrays.copyOf(data, (data.length << 1) + data.length >>> 1);
            }
            data[sp++] = value;
        }

        @SuppressWarnings("unchecked")
        public T pop() {
            try {
                return (T) data[--sp];
            } finally {
                data[sp] = null;
            }
        }

        @SuppressWarnings("unchecked")
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

        @SuppressWarnings("unchecked")
        public T get(int n) {
            return n < sp ? (T) data[n] : null;
        }

        public String toString() {
            final StringBuilder b = new StringBuilder();
            final int sp = this.sp;
            for (int i = 0; i < sp; i++) {
                b.append(data[i]);
                if ((i + 1) < sp) {
                    b.append('.');
                }
            }
            return b.toString();
        }
    }
}
