/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import org.junit.After;
import org.junit.Before;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractTest {

    @Before
    public void addLogContext() {
        final LogContext context = LogContext.create();
        LogContext.setLogContextSelector(new LogContextSelector() {
            @Override
            public LogContext getLogContext() {
                return context;
            }
        });
    }

    @After
    public void removeLogContext() {
        LogContext.setLogContextSelector(LogContext.DEFAULT_LOG_CONTEXT_SELECTOR);
    }
}
