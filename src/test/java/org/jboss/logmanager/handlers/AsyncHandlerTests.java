/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.handlers;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.MDC;
import org.jboss.logmanager.NDC;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.AsyncHandler.OverflowAction;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AsyncHandlerTests {

    private BlockingQueueHandler handler;
    private AsyncHandler asyncHandler;

    @Before
    public void setup() {
        handler = new BlockingQueueHandler();

        asyncHandler = new AsyncHandler();
        asyncHandler.setOverflowAction(OverflowAction.DISCARD);
        asyncHandler.addHandler(handler);
    }

    @After
    public void tearDown() {
        asyncHandler.close();
        handler.close();
        NDC.clear();
        MDC.clear();
    }

    @Test
    public void testNdc() throws Exception {
        handler.setFormatter(new PatternFormatter("%x"));
        String ndcValue = "Test NDC value";
        NDC.push(ndcValue);
        asyncHandler.doPublish(createRecord());
        Assert.assertEquals(ndcValue, NDC.pop());
        Assert.assertEquals(ndcValue, handler.getFirst());

        // Next value should be blank
        asyncHandler.doPublish(createRecord());
        Assert.assertEquals("", handler.getFirst());

        ndcValue = "New test NDC value";
        NDC.push(ndcValue);
        asyncHandler.doPublish(createRecord());
        NDC.push("invalid");
        Assert.assertEquals(ndcValue, handler.getFirst());
    }

    @Test
    public void testMdc() throws Exception {
        handler.setFormatter(new PatternFormatter("%X{key}"));
        String mdcValue = "Test MDC value";
        MDC.put("key", mdcValue);
        asyncHandler.doPublish(createRecord());
        MDC.remove("key");
        Assert.assertEquals(mdcValue, handler.getFirst());

        asyncHandler.doPublish(createRecord());
        Assert.assertEquals("", handler.getFirst());

        mdcValue = "New test MDC value";
        MDC.put("key", mdcValue);
        asyncHandler.doPublish(createRecord());
        MDC.put("key", "invalid");
        Assert.assertEquals(mdcValue, handler.getFirst());
    }

    static ExtLogRecord createRecord() {
        return new ExtLogRecord(Level.INFO, "Test message", AsyncHandlerTests.class.getName());
    }

    static class BlockingQueueHandler extends ExtHandler {
        private final BlockingDeque<String> queue;

        BlockingQueueHandler() {
            queue = new LinkedBlockingDeque<String>();
        }

        @Override
        protected void doPublish(final ExtLogRecord record) {
            queue.addLast(getFormatter().format(record));
            super.doPublish(record);
        }

        String getFirst() throws InterruptedException {
            return queue.pollFirst(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws SecurityException {
            queue.clear();
        }
    }
}
