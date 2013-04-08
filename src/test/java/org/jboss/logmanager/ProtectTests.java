/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
