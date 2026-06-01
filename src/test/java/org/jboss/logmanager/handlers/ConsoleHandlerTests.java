/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

import static org.junit.jupiter.api.Assertions.*;

import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Test;

public class ConsoleHandlerTests {

    @Test
    public void testDefaultTarget() {
        final ConsoleHandler handler = new ConsoleHandler();
        final ConsoleHandler.Target target = handler.getTarget();
        assertNotNull(target);
        // Default should be CONSOLE if a console exists, SYSTEM_OUT otherwise
        if (ConsoleHandler.hasConsole()) {
            assertEquals(ConsoleHandler.Target.CONSOLE, target);
        } else {
            assertEquals(ConsoleHandler.Target.SYSTEM_OUT, target);
        }
    }

    @Test
    public void testConstructorWithTarget() {
        final ConsoleHandler handler = new ConsoleHandler(ConsoleHandler.Target.SYSTEM_ERR);
        assertEquals(ConsoleHandler.Target.SYSTEM_ERR, handler.getTarget());
    }

    @Test
    public void testConstructorWithTargetAndFormatter() {
        final ConsoleHandler handler = new ConsoleHandler(ConsoleHandler.Target.SYSTEM_OUT, new PatternFormatter("%m"));
        assertEquals(ConsoleHandler.Target.SYSTEM_OUT, handler.getTarget());
    }

    @Test
    public void testSetTarget() {
        final ConsoleHandler handler = new ConsoleHandler(ConsoleHandler.Target.SYSTEM_OUT);
        assertEquals(ConsoleHandler.Target.SYSTEM_OUT, handler.getTarget());

        handler.setTarget(ConsoleHandler.Target.SYSTEM_ERR);
        assertEquals(ConsoleHandler.Target.SYSTEM_ERR, handler.getTarget());

        handler.setTarget(ConsoleHandler.Target.SYSTEM_OUT);
        assertEquals(ConsoleHandler.Target.SYSTEM_OUT, handler.getTarget());
    }

    @Test
    public void testSetTargetNull() {
        final ConsoleHandler handler = new ConsoleHandler(ConsoleHandler.Target.SYSTEM_ERR);
        handler.setTarget(null);
        // null defaults to the default target
        if (ConsoleHandler.hasConsole()) {
            assertEquals(ConsoleHandler.Target.CONSOLE, handler.getTarget());
        } else {
            assertEquals(ConsoleHandler.Target.SYSTEM_OUT, handler.getTarget());
        }
    }
}
