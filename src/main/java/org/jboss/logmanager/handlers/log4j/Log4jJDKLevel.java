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

package org.jboss.logmanager.handlers.log4j;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Level;

/**
 * Log4j equivalents for JDK logging levels.  For configuration file usage.
 */
public final class Log4jJDKLevel extends Level {

    private static final long serialVersionUID = -2456662804627419121L;

    /**
     * Instantiate a Level object.
     */
    protected Log4jJDKLevel(int level, String levelStr, int syslogEquivalent) {
        super(level, levelStr, syslogEquivalent);
    }

    /**
     * A mapping of the JDK logging {@link java.util.logging.Level#SEVERE SEVERE} level; numerically
     * equivalent to log4j's {@link org.apache.log4j.Level#ERROR ERROR} level.
     */
    public static final Level SEVERE = new Log4jJDKLevel(Level.ERROR_INT, "SEVERE", 3);

    /**
     * A mapping of the JDK logging {@link java.util.logging.Level#WARNING WARNING} level; numerically
     * equivalent to log4j's {@link org.apache.log4j.Level#WARN WARN} level.
     */
    public static final Level WARNING = new Log4jJDKLevel(Level.WARN_INT, "WARNING", 4);

    /**
     * A mapping of the JDK logging {@link java.util.logging.Level#INFO INFO} level; numerically
     * equivalent to log4j's {@link org.apache.log4j.Level#INFO INFO} level.
     */
    public static final Level INFO = new Log4jJDKLevel(Level.INFO_INT, "INFO", 5);

    /**
     * A mapping of the JDK logging {@link java.util.logging.Level#CONFIG CONFIG} level; numerically
     * falls between log4j's {@link org.apache.log4j.Level#INFO INFO} and {@link org.apache.log4j.Level#DEBUG DEBUG} levels.
     */
    public static final Level CONFIG = new Log4jJDKLevel(Level.INFO_INT - 5000, "CONFIG", 6);

    /**
     * A mapping of the JDK logging {@link java.util.logging.Level#FINE FINE} level; numerically
     * equivalent to log4j's {@link org.apache.log4j.Level#DEBUG DEBUG} level.
     */
    public static final Level FINE = new Log4jJDKLevel(Level.DEBUG_INT, "FINE", 7);

    /**
     * A mapping of the JDK logging {@link java.util.logging.Level#FINER FINER} level; numerically
     * falls between log4j's {@link org.apache.log4j.Level#DEBUG DEBUG} and {@link org.apache.log4j.Level#TRACE TRACE} levels.
     */
    public static final Level FINER = new Log4jJDKLevel(Level.DEBUG_INT - 2500, "FINER", 7);

    /**
     * A mapping of the JDK logging {@link java.util.logging.Level#FINEST FINEST} level; numerically
     * equivalent to log4j's {@link org.apache.log4j.Level#TRACE TRACE} level.
     */
    public static final Level FINEST = new Log4jJDKLevel(Level.TRACE_INT, "FINEST", 7);

    private static final Map<String, Level> levelMapping = new HashMap<String, Level>();

    private static void add(Level lvl) {
        levelMapping.put(lvl.toString(), lvl);
    }

    static {
        add(SEVERE);
        add(WARNING);
        add(INFO);
        add(CONFIG);
        add(FINE);
        add(FINER);
        add(FINEST);
    }

    /**
     * Get the level for the given name.  If the level is not one of the levels defined in this class,
     * this method delegates to {@link org.apache.log4j.Level#toLevel(String) toLevel(String)} on the superclass.
     *
     * @param name the level name
     * @return the equivalent level
     */
    public static Level toLevel(String name) {
        final Level level = levelMapping.get(name.trim().toUpperCase());
        return level != null ? level : Level.toLevel(name);
    }
}