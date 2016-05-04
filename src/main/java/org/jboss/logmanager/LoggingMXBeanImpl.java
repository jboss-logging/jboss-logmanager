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
        names.add(node.getFullName());
        for (LoggerNode loggerNode : node.getChildren()) {
            if (loggerNode != null) getAllNames(names, loggerNode);
        }
    }

    @Override
    public List<String> getLoggerNames() {
        final LoggerNode node = context.getRootLoggerNode();
        final ArrayList<String> names = new ArrayList<String>();
        getAllNames(names, node);
        return names;
    }

    @Override
    public String getLoggerLevel(final String loggerName) {
        final LoggerNode loggerNode = context.getRootLoggerNode().getIfExists(loggerName);
        final Level level = loggerNode == null ? null : loggerNode.getLevel();
        return level == null ? "" : level.getName();
    }

    @Override
    public void setLoggerLevel(final String loggerName, final String levelName) {
        final LoggerNode loggerNode = context.getRootLoggerNode().getIfExists(loggerName);
        if (loggerNode == null) {
            throw new IllegalArgumentException("logger \"" + loggerName + "\" does not exist");
        }
        loggerNode.setLevel(levelName == null ? null : context.getLevelForName(levelName));
    }

    @Override
    public String getParentLoggerName(final String loggerName) {
        final int dotIdx = loggerName.lastIndexOf('.');
        if (dotIdx == -1) {
            return "";
        } else {
            return loggerName.substring(0, dotIdx);
        }
    }
}
