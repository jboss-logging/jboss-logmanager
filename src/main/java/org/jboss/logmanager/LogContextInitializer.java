/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * An initializer for log contexts.  The initializer provides initial values for log instances within the context
 * for properties like levels, handlers, and so on.
 * <p>
 * The initial log context will be configured using a context initializer that is located via the {@linkplain java.util.ServiceLoader JDK SPI mechanism}.
 * <p>
 * This interface is intended to be forward-extensible.  If new methods are added, they will include a default implementation.
 * Implementations of this interface should accommodate the possibility of new methods being added; as a matter of convention,
 * such methods should begin with the prefix {@code getInitial}, which will minimize the possibility of conflict.
 */
public interface LogContextInitializer {
    /**
     * An array containing zero handlers.
     */
    Handler[] NO_HANDLERS = new Handler[0];

    /**
     * The default log context initializer, which is used when none is specified.  This instance uses only
     * default implementations for all the given methods.
     */
    LogContextInitializer DEFAULT = new LogContextInitializer() {
    };

    /**
     * Get the initial level for the given logger name.  If the initializer returns a {@code null} level for the
     * root logger, then a level of {@link org.jboss.logmanager.Level#INFO INFO} will be used.
     * <p>
     * The default implementation returns {@code null}.
     *
     * @param loggerName the logger name (must not be {@code null})
     * @return the level to use, or {@code null} to inherit the level from the parent
     */
    default Level getInitialLevel(String loggerName) {
        return null;
    }

    /**
     * Get the minimum (most verbose) level allowed for the given logger name.  If the initializer returns a
     * {@code null} level for the root logger, then a level of {@link java.util.logging.Level#ALL ALL} will be used.
     * <p>
     * The default implementation returns {@code null}.
     *
     * @param loggerName the logger name (must not be {@code null})
     * @return the level to use, or {@code null} to inherit the level from the parent
     */
    default Level getMinimumLevel(String loggerName) {
        return null;
    }

    /**
     * Get the initial set of handlers to configure for the given logger name.
     * <p>
     * The default implementation returns {@link #NO_HANDLERS}.  A value of {@code null} is considered
     * to be the same as {@link #NO_HANDLERS}.
     *
     * @param loggerName the logger name (must not be {@code null})
     * @return the handlers to use (should not be {@code null})
     */
    default Handler[] getInitialHandlers(String loggerName) {
        return NO_HANDLERS;
    }

    /**
     * Establish whether strong references should be used for logger nodes.
     *
     * @return {@code true} to use strong references, or {@code false} to use weak references
     */
    default boolean useStrongReferences() {
        return false;
    }
}
