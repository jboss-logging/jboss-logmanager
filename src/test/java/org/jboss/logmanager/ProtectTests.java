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

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ProtectTests {

    @Test
    public void testLogContextLock() {
        final Object lock = new Object();
        final Logger logger = Logger.getLogger("org.jboss.logmanager.test");
        final LogContext logContext = LogContext.getLogContext();
        logContext.protect(lock);
        // Should throw a SecurityException
        try {
            logger.setLevel(Level.INFO);
            Assert.fail("Logger not locked");
        } catch (SecurityException e) {
            // no-op
        }
        // Unlock the logger
        logContext.enableAccess(lock);
        logger.setLevel(Level.INFO);
        logContext.disableAccess();
        // Should throw a SecurityException
        try {
            logger.setLevel(Level.ALL);
            Assert.fail("Logger not locked");
        } catch (SecurityException e) {
            // no-op
        }

        // Invalid lock Should throw a SecurityException
        try {
            logContext.unprotect(new Object());
            Assert.fail("Unprotect passed with invalid object");
        } catch (SecurityException e) {
            // no-op
        }
        // Unprotect the logger
        logContext.unprotect(lock);
        logger.setLevel(Level.INFO);
    }

    @Test
    public void testHandlerLock() {
        final Object lock = new Object();
        final Logger logger = Logger.getLogger("org.jboss.logmanager.test");
        final StringListHandler handler = new StringListHandler();
        logger.addHandler(handler);
        logger.info("Test message");
        handler.protect(lock);
        // Should throw a SecurityException
        try {
            handler.setLevel(Level.INFO);
            Assert.fail("Handler not locked");
        } catch (SecurityException e) {
            // no-op
        }
        logger.info("Test message");
        Assert.assertEquals(handler.size(), 2);
        handler.enableAccess(lock);

        handler.enableAccess(lock);
        handler.setLevel(Level.INFO);
        handler.disableAccess();
        // Should throw a SecurityException
        try {
            handler.setLevel(Level.ALL);
            Assert.fail("Logger not locked");
        } catch (SecurityException e) {
            // no-op
        }

        // Invalid lock Should throw a SecurityException
        try {
            handler.unprotect(new Object());
            Assert.fail("Unprotect passed with invalid object");
        } catch (SecurityException e) {
            // no-op
        }
        // Unprotect the logger
        handler.unprotect(lock);
        logger.setLevel(Level.INFO);
    }

}
