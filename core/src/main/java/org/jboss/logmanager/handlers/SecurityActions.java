/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.handlers;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class SecurityActions {

    static ErrorManager getErrorManager(final AccessControlContext acc, final Handler handler) {
        Supplier<ErrorManager> supplier = () -> {
            if (System.getSecurityManager() == null) {
                return handler.getErrorManager();
            }
            return AccessController.doPrivileged((PrivilegedAction<ErrorManager>) handler::getErrorManager, acc);
        };
        return new LazyErrorManager(supplier);
    }

    private static class LazyErrorManager extends ErrorManager {
        private final Supplier<ErrorManager> supplier;
        private volatile ErrorManager delegate;

        private LazyErrorManager(final Supplier<ErrorManager> supplier) {
            this.supplier = supplier;
        }

        @Override
        public synchronized void error(final String msg, final Exception ex, final int code) {
            getDelegate().error(msg, ex, code);
        }

        private ErrorManager getDelegate() {
            if (delegate == null) {
                synchronized (this) {
                    if (delegate == null) {
                        delegate = supplier.get();
                    }
                }
            }
            return delegate;
        }
    }
}
