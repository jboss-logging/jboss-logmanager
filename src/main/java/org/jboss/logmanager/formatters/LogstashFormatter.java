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
import java.util.Map;

import org.jboss.logmanager.ExtLogRecord;

/**
 * A {@link JsonFormatter JSON formatter} which adds the {@code @version} to the generated JSON and overrides the
 * {@code timestamp} key to {@code @timestamp}.
 * <p>
 * The default {@link #getVersion() version} is {@code 1}.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("WeakerAccess")
public class LogstashFormatter extends JsonFormatter {

    private volatile int version = 1;

    /**
     * Create the lostash formatter.
     */
    public LogstashFormatter() {
        this(Collections.singletonMap(Key.TIMESTAMP, "@timestamp"));
    }

    /**
     * Create the logstash formatter overriding any default keys
     *
     * @param keyOverrides the keys used to override the defaults
     */
    public LogstashFormatter(final Map<Key, String> keyOverrides) {
        super(keyOverrides);
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
}
