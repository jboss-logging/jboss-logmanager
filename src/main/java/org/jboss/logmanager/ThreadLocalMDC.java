package org.jboss.logmanager;

import java.util.Map;

final class ThreadLocalMDC implements MDCProvider {
    private static final Holder mdc = new Holder();

    @Override
    public String get(String key) {
        final Object value = getObject(key);
        return value == null ? null : value.toString();
    }

    @Override
    public Object getObject(String key) {
        return mdc.get().get(key);
    }

    @Override
    public String put(String key, String value) {
        final Object oldValue = putObject(key, value);
        return oldValue == null ? null : oldValue.toString();
    }

    @Override
    public Object putObject(String key, Object value) {
        if (key == null) {
            throw new NullPointerException("key is null");
        }
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        return mdc.get().put(key, value);
    }

    @Override
    public String remove(String key) {
        final Object oldValue = removeObject(key);
        return oldValue == null ? null : oldValue.toString();
    }

    @Override
    public Object removeObject(String key) {
        return mdc.get().remove(key);
    }

    @Override
    public Map<String, String> copy() {
        final FastCopyHashMap<String, String> result = new FastCopyHashMap<>();
        for (Map.Entry<String, Object> entry : mdc.get().entrySet()) {
            result.put(entry.getKey(), entry.getValue().toString());
        }
        return result;
    }

    @Override
    public Map<String, Object> copyObject() {
        return mdc.get().clone();
    }

    @Override
    public boolean isEmpty() {
        return mdc.get().isEmpty();
    }

    @Override
    public void clear() {
        mdc.get().clear();
    }

    private static final class Holder extends InheritableThreadLocal<FastCopyHashMap<String, Object>> {

        @Override
        protected FastCopyHashMap<String, Object> childValue(final FastCopyHashMap<String, Object> parentValue) {
            return new FastCopyHashMap<>(parentValue);
        }

        @Override
        protected FastCopyHashMap<String, Object> initialValue() {
            return new FastCopyHashMap<>();
        }
    }
}
