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

package org.jboss.logmanager.handlers;

import org.jboss.logmanager.ExtHandler;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class HandlerTests {

    @Test
    public void testHandlerClose() throws Exception {
        final CloseHandler parent = new CloseHandler();
        final CloseHandler child1 = new CloseHandler();
        final CloseHandler child2 = new CloseHandler();
        parent.setHandlers(new CloseHandler[] {child1, child2, new CloseHandler()});

        // Ensure all handlers are not closed
        Assert.assertFalse(parent.closed);
        Assert.assertFalse(child1.closed);
        Assert.assertFalse(child2.closed);

        // Close the parent handler, the children should be closed
        parent.close();
        Assert.assertTrue(parent.closed);
        Assert.assertTrue(child1.closed);
        Assert.assertTrue(child2.closed);

        // Reset and wrap
        parent.reset();
        child1.reset();
        child2.reset();

        parent.setCloseChildren(false);

        // Ensure all handlers are not closed
        Assert.assertFalse(parent.closed);
        Assert.assertFalse(child1.closed);
        Assert.assertFalse(child2.closed);

        parent.close();

        // The parent should be closed, the others should be open
        Assert.assertTrue(parent.closed);
        Assert.assertFalse(child1.closed);
        Assert.assertFalse(child2.closed);

    }

    static class CloseHandler extends ExtHandler {
        private boolean closed = false;

        @Override
        public void close() {
            closed = true;
            super.close();
        }

        void reset() {
            closed = false;
        }
    }
}
