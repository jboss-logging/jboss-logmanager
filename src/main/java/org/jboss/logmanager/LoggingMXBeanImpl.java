/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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

import java.util.ArrayList;
import java.util.List;

import java.util.logging.Level;
import java.util.logging.LoggingMXBean;

final class LoggingMXBeanImpl implements LoggingMXBean {
    private final LogContext context;

    LoggingMXBeanImpl(final LogContext context) {
        this.context = context;
    }

    private void getAllNames(List<String> names, LoggerNode node) {
        final Logger logger = node.getLogger();
        if (logger != null) {
            names.add(logger.getName());
        }
        for (LoggerNode loggerNode : node.getChildren()) {
            getAllNames(names, loggerNode);
        }
    }

    public List<String> getLoggerNames() {
        final LoggerNode node = context.getRootLoggerNode();
        final ArrayList<String> names = new ArrayList<String>();
        getAllNames(names, node);
        return names;
    }

    public String getLoggerLevel(final String loggerName) {
        final Logger logger = context.getLoggerIfExists(loggerName);
        final Level level = logger == null ? null : logger.getLevel();
        return level == null ? "" : level.getName();
    }

    public void setLoggerLevel(final String loggerName, final String levelName) {
        final Logger logger = context.getLoggerIfExists(loggerName);
        if (logger == null) {
            throw new IllegalArgumentException("logger \"" + loggerName + "\" does not exist");
        }
        logger.setLevel(levelName == null ? null : Level.parse(levelName));
    }

    public String getParentLoggerName(final String loggerName) {
        final Logger logger = context.getLoggerIfExists(loggerName);
        if (logger == null) {
            return null;
        }
        final Logger parent = logger.getParent();
        return parent == null ? "" : parent.getName();
    }
}
