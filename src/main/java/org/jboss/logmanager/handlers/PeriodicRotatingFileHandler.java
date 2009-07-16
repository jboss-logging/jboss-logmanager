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

package org.jboss.logmanager.handlers;

import org.jboss.logmanager.ExtLogRecord;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.io.File;
import java.io.FileNotFoundException;

import java.util.logging.ErrorManager;

/**
 * A file handler which rotates the log at a preset time interval.  The interval is determined by the content of the
 * suffix string which is passed in to {@link #setSuffix(String)}.
 */
public class PeriodicRotatingFileHandler extends FileHandler {

    private SimpleDateFormat format;
    private String nextSuffix;
    private Period period = Period.NEVER;
    private long nextRollover = Long.MAX_VALUE;

    /**
     * Construct a new instance with no formatter and no output file.
     */
    public PeriodicRotatingFileHandler() {
    }

    /**
     * Construct a new instance with the given output file.
     *
     * @param file the file
     * @param suffix the format suffix to use
     *
     * @throws java.io.FileNotFoundException if the file could not be found on open
     */
    public PeriodicRotatingFileHandler(final File file, final String suffix) throws FileNotFoundException {
        super(file);
        setSuffix(suffix);
    }

    /**
     * Construct a new instance with the given output file and append setting.
     *
     * @param file the file
     * @param suffix the format suffix to use
     * @param append {@code true} to append, {@code false} to overwrite
     * @throws java.io.FileNotFoundException if the file could not be found on open
     */
    public PeriodicRotatingFileHandler(final File file, final String suffix, final boolean append) throws FileNotFoundException {
        super(file, append);
        setSuffix(suffix);
    }

    /** {@inheritDoc}  This implementation checks to see if the scheduled rollover time has yet occurred. */
    protected void preWrite(final ExtLogRecord record) {
        final long recordMillis = record.getMillis();
        if (recordMillis >= nextRollover) {
            rollOver();
            calcNextRollover(recordMillis);
        }
    }

    /**
     * Set the suffix string.  The string is in a format which can be understood by {@link java.text.SimpleDateFormat}.
     * The period of the rotation is automatically calculated based on the suffix.
     *
     * @param suffix the suffix
     * @throws IllegalArgumentException if the suffix is not valid
     */
    public void setSuffix(String suffix) throws IllegalArgumentException {
        final SimpleDateFormat format = new SimpleDateFormat(suffix);
        final int len = suffix.length();
        Period period = Period.NEVER;
        for (int i = 0; i < len; i ++) {
            switch (suffix.charAt(i)) {
                case 'y': period = min(period, Period.YEAR); break;
                case 'M': period = min(period, Period.MONTH); break;
                case 'w':
                case 'W': period = min(period, Period.WEEK); break;
                case 'D':
                case 'd':
                case 'F':
                case 'E': period = min(period, Period.DAY); break;
                case 'a': period = min(period, Period.HALF_DAY); break;
                case 'H':
                case 'k':
                case 'K':
                case 'h': period = min(period, Period.HOUR); break;
                case 'm': period = min(period, Period.MINUTE); break;
                case '\'': while (suffix.charAt(++i) != '\''); break;
                case 's':
                case 'S': throw new IllegalArgumentException("Rotating by second or millisecond is not supported");
            }
        }
        synchronized (outputLock) {
            this.format = format;
            this.period = period;
            final long now = System.currentTimeMillis();
            calcNextRollover(now);
        }
    }

    private void rollOver() {
        try {
            final File file = getFile();
            // first, close the original file (some OSes won't let you move/rename a file that is open)
            setFile(null);
            // next, rotate it
            file.renameTo(new File(file.getName() + nextSuffix));
            // start new file
            setFile(file);
        } catch (FileNotFoundException e) {
            reportError("Unable to rotate log file", e, ErrorManager.OPEN_FAILURE);
        }
    }

    private void calcNextRollover(final long fromTime) {
        if (period == Period.NEVER) {
            nextRollover = Long.MAX_VALUE;
            return;
        }
        nextSuffix = format.format(new Date(fromTime));
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(fromTime);
        final Period period = this.period;
        // clear out less-significant fields
        switch (period) {
            default:
            case YEAR:
                calendar.clear(Calendar.MONTH);
            case MONTH:
                calendar.clear(Calendar.DAY_OF_MONTH);
                calendar.clear(Calendar.WEEK_OF_MONTH);
            case WEEK:
                calendar.clear(Calendar.DAY_OF_WEEK);
                calendar.clear(Calendar.DAY_OF_WEEK_IN_MONTH);
            case DAY:
                calendar.clear(Calendar.HOUR_OF_DAY);
            case HALF_DAY:
                calendar.clear(Calendar.HOUR);
            case HOUR:
                calendar.clear(Calendar.MINUTE);
            case MINUTE:
                calendar.clear(Calendar.SECOND);
                calendar.clear(Calendar.MILLISECOND);
        }
        // increment the relevant field
        switch (period) {
            case YEAR:
                calendar.add(Calendar.YEAR, 1);
                break;
            case MONTH:
                calendar.add(Calendar.MONTH, 1);
                break;
            case WEEK:
                calendar.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case DAY:
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case HALF_DAY:
                calendar.add(Calendar.AM_PM, 1);
                break;
            case HOUR:
                calendar.add(Calendar.HOUR, 1);
                break;
            case MINUTE:
                calendar.add(Calendar.MINUTE, 1);
                break;
        }
        nextRollover = calendar.getTimeInMillis();
    }

    private static <T extends Comparable<? super T>> T min(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * Possible period values.  Keep in strictly ascending order of magnitude.
     */
    public enum Period {
        MINUTE,
        HOUR,
        HALF_DAY,
        DAY,
        WEEK,
        MONTH,
        YEAR,
        NEVER,
    }
}
