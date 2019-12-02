/*
 * JBoss, Home of Professional Open Source.
 *
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

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
interface ContextualLoggerNode {

    @SuppressWarnings({"Convert2Lambda"})
    static ContextualLoggerNode of(final LoggerNode defaultNode, final String name) {
        return new ContextualLoggerNode() {
            @Override
            public LoggerNode getLoggerNode() {
                if (LogContextRouter.isEnabled()) {
                    final LoggerNode result = LogContextRouter.getInstance().getLoggerNode(name);
                    return result == null ? defaultNode : result;
                }
                return defaultNode;
            }
        };
    }

    default void decrementRef() {
        getLoggerNode().decrementRef();
    }

    default LogContext getContext() {
        return getLoggerNode().getContext();
    }

    default void setFilter(final Filter filter) {
        getLoggerNode().setFilter(filter);
    }

    default Filter getFilter() {
        return getLoggerNode().getFilter();
    }

    default boolean getUseParentFilters() {
        return getLoggerNode().getUseParentFilters();
    }

    default void setUseParentFilters(final boolean useParentFilter) {
        getLoggerNode().setUseParentFilters(useParentFilter);
    }

    default int getEffectiveLevel() {
        return getLoggerNode().getEffectiveLevel();
    }

    default Handler[] getHandlers() {
        return getLoggerNode().getHandlers();
    }

    default Handler[] clearHandlers() {
        return getLoggerNode().clearHandlers();
    }

    default void removeHandler(final Handler handler) {
        getLoggerNode().removeHandler(handler);
    }

    default void addHandler(final Handler handler) {
        getLoggerNode().addHandler(handler);
    }

    default Handler[] setHandlers(final Handler[] handlers) {
        return getLoggerNode().setHandlers(handlers);
    }

    default boolean compareAndSetHandlers(final Handler[] oldHandlers, final Handler[] newHandlers) {
        return getLoggerNode().compareAndSetHandlers(oldHandlers, newHandlers);
    }

    default boolean getUseParentHandlers() {
        return getLoggerNode().getUseParentHandlers();
    }

    default void setUseParentHandlers(final boolean useParentHandlers) {
        getLoggerNode().setUseParentHandlers(useParentHandlers);
    }

    default void publish(final ExtLogRecord record) {
        getLoggerNode().publish(record);
    }

    default void setLevel(final Level newLevel) {
        getLoggerNode().setLevel(newLevel);
    }

    default Level getLevel() {
        return getLoggerNode().getLevel();
    }

    default <V> V getAttachment(final Logger.AttachmentKey<V> key) {
        return getLoggerNode().getAttachment(key);
    }

    default <V> V attach(final Logger.AttachmentKey<V> key, final V value) {
        return getLoggerNode().attach(key, value);
    }

    default <V> V attachIfAbsent(final Logger.AttachmentKey<V> key, final V value) {
        return getLoggerNode().attachIfAbsent(key, value);
    }

    default <V> V detach(final Logger.AttachmentKey<V> key) {
        return getLoggerNode().detach(key);
    }

    default LoggerNode getParent() {
        return getLoggerNode().getParent();
    }

    default boolean isLoggable(final ExtLogRecord record) {
        return getLoggerNode().isLoggable(record);
    }

    LoggerNode getLoggerNode();
}
