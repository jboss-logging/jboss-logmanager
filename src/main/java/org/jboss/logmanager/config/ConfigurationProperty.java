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
 * Represents a configuration property.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ConfigurationProperty {

    /**
     * Indicates whether or not the property should be perisisted to the configuration.
     *
     * @return {@code true} if the property should be persisted, otherwise {@code false}
     */
    boolean isPersistable();

    /**
     * The key for the property.
     *
     * @return the key
     */
    String getKey();

    /**
     * The properties value.
     *
     * @return the value
     */
    ValueExpression<String> getValue();

    /**
     * The properties value which may or may not be an expression. If {@code allowExpression} is {@code false} a
     * string representation of the {@linkplain ValueExpression#getResolvedValue() resolved value} will be returned.
     * Otherwise a string which may be an expression will be returned.
     *
     * @param allowExpression {@code true} if a possible expression is allowed to be returned {@code false} if any
     *                        possible expressions should be resovled
     *
     * @return a string representation of the value
     *
     * @see ValueExpression#getValue()
     */
    default String getValue(final boolean allowExpression) {
        final ValueExpression<String> value = getValue();
        if (value == null) {
            return "";
        }
        return allowExpression ? value.getValue() : value.getResolvedValue();
    }
}
