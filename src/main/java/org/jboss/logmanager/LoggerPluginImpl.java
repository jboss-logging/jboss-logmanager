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

import org.jboss.logging.LoggerPlugin;
import org.jboss.logging.NDCSupport;
import org.jboss.logging.MDCSupport;
import org.jboss.logging.NDCProvider;
import org.jboss.logging.MDCProvider;

public final class LoggerPluginImpl implements LoggerPlugin, NDCSupport, MDCSupport {

    private Logger logger;

    public void init(final String name) {
        logger = Logger.getLogger(name);
    }

    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    public void trace(final Object message) {
        logger.trace(String.valueOf(message));
    }

    public void trace(final Object message, final Throwable t) {
        logger.trace(String.valueOf(message), t);
    }

    @Deprecated
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void debug(final Object message) {
        logger.debug(String.valueOf(message));
    }

    public void debug(final Object message, final Throwable t) {
        logger.debug(String.valueOf(message), t);
    }

    @Deprecated
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public void info(final Object message) {
        logger.info(String.valueOf(message));
    }

    public void info(final Object message, final Throwable t) {
        logger.info(String.valueOf(message), t);
    }

    public void warn(final Object message) {
        logger.warn(String.valueOf(message));
    }

    public void warn(final Object message, final Throwable t) {
        logger.warn(String.valueOf(message), t);
    }

    public void error(final Object message) {
        logger.error(String.valueOf(message));
    }

    public void error(final Object message, final Throwable t) {
        logger.error(String.valueOf(message), t);
    }

    public void fatal(final Object message) {
        logger.fatal(String.valueOf(message));
    }

    public void fatal(final Object message, final Throwable t) {
        logger.fatal(String.valueOf(message), t);
    }

    public NDCProvider getNDCProvider() {
        return NDCProviderImpl.getInstance();
    }

    public MDCProvider getMDCProvider() {
        return MDCProviderImpl.getInstance();
    }
}
