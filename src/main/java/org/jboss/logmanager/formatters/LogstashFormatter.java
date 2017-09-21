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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.PropertyValues;

/**
 * A {@link JsonFormatter JSON formatter} which adds the {@code @version} to the generated JSON and overrides the
 * {@code timestamp} key to {@code @timestamp}.
 * <p>
 * The default {@link #getVersion() version} is {@code 1}.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class LogstashFormatter extends JsonFormatter {

    private volatile int version = 1;


    /**
     * Creates a new logstash formatter.
     */
    public LogstashFormatter() {
        this(Collections.singletonMap(Key.TIMESTAMP, "@timestamp"));
    }

    /**
     * Creates a new logstash formatter.
     *
     * @param keyOverrides a string representation of a map to override keys
     *
     * @see PropertyValues#stringToEnumMap(Class, String)
     */
    public LogstashFormatter(final String keyOverrides) {
        super(keyOverrides(keyOverrides));
    }

    /**
     * Creates a new logstash formatter.
     *
     * @param keyOverrides a map of overrides for the default keys
     */
    public LogstashFormatter(final Map<Key, String> keyOverrides) {
        super(keyOverrides(keyOverrides));
    }

    @Override
    protected void before(final Generator generator, final ExtLogRecord record) throws Exception {
        generator.add("@version", version);
    }

    /**
     * Returns the version being used for the {@code @version} property.
     *
     * @return the version being used
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets the version to use for the {@code @version} property.
     *
     * @param version the version to use
     */
    public void setVersion(final int version) {
        this.version = version;
    }

    private static Map<Key, String> keyOverrides(final Map<Key, String> keyOverrides) {
        if (keyOverrides.containsKey(Key.TIMESTAMP)) {
            return keyOverrides;
        }
        final EnumMap<Key, String> result = new EnumMap<>(Key.class);
        result.putAll(keyOverrides);
        result.put(Key.TIMESTAMP, "@timestamp");
        return result;
    }

    private static String keyOverrides(final String keyOverrides) {
        if (keyOverrides == null || keyOverrides.trim().isEmpty()) {
            return Key.TIMESTAMP.name() + "=@timestamp";
        }
        if (keyOverrides.toUpperCase(Locale.ROOT).contains(Key.TIMESTAMP.name())) {
            return keyOverrides;
        }
        return keyOverrides + "," + Key.TIMESTAMP.name() + "=@timestamp";
    }
}
