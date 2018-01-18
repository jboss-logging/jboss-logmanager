/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.config;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ConfigurationPropertyImpl implements ConfigurationProperty {

    private final String key;
    private final ValueExpression<String> value;
    private final boolean writable;

    ConfigurationPropertyImpl(final String key, final ValueExpression<String> value, final boolean writable) {
        this.key = key;
        this.value = value == null ? ValueExpression.NULL_STRING_EXPRESSION : value;
        this.writable = writable;
    }

    @Override
    public boolean isPersistable() {
        return writable;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public ValueExpression<String> getValue() {
        return value;
    }
}
