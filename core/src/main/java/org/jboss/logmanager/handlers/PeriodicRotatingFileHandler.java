/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import java.util.logging.ErrorManager;

import org.jboss.logmanager.ExtLogRecord;

/**
 * A file handler which rotates the log at a preset time interval.  The interval is determined by the content of the
 * suffix string which is passed in to {@link #setSuffix(String)}.
 */
public class PeriodicRotatingFileHandler extends FileHandler {

    private final AccessControlContext acc = AccessController.getContext();
    private DateTimeFormatter format;
    private String nextSuffix;
    private Period period = Period.NEVER;
    private Instant nextRollover = Instant.MAX;
    private TimeZone timeZone = TimeZone.getDefault();
    private SuffixRotator suffixRotator = SuffixRotator.EMPTY;

    /**
     * Construct a new instance with no formatter and no output file.
     */
    public PeriodicRotatingFileHandler() {
    }

    /**
     * Construct a new instance with the given output file.
     *
     * @param fileName the file name
     *
     * @throws FileNotFoundException if the file could not be found on open
     */
    public PeriodicRotatingFileHandler(final String fileName) throws FileNotFoundException {
        super(fileName);
    }

    /**
     * Construct a new instance with the given output file and append setting.
     *
     * @param fileName the file name
     * @param append {@code true} to append, {@code false} to overwrite
     *
     * @throws FileNotFoundException if the file could not be found on open
     */
    public PeriodicRotatingFileHandler(final String fileName, final boolean append) throws FileNotFoundException {
        super(fileName, append);
    }

    /**
     * Construct a new instance with the given output file.
     *
     * @param file the file
     * @param suffix the format suffix to use
     *
     * @throws FileNotFoundException if the file could not be found on open
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
     * @throws FileNotFoundException if the file could not be found on open
     */
    public PeriodicRotatingFileHandler(final File file, final String suffix, final boolean append) throws FileNotFoundException {
        super(file, append);
        setSuffix(suffix);
    }

    @Override
    public void setFile(final File file) throws FileNotFoundException {
        synchronized (outputLock) {
            super.setFile(file);
            if (format != null && file != null && file.lastModified() > 0) {
                calcNextRollover(Instant.ofEpochMilli(file.lastModified()));
            }
        }
    }

    /** {@inheritDoc}  This implementation checks to see if the scheduled rollover time has yet occurred. */
    protected void preWrite(final ExtLogRecord record) {
        Instant recordInstant = record.getInstant();
        if (! recordInstant.isBefore(nextRollover)) {
            rollOver();
            calcNextRollover(recordInstant);
        }
    }

    /**
     * Set the suffix string.  The string is in a format which can be understood by {@link DateTimeFormatter}.
     * The period of the rotation is automatically calculated based on the suffix.
     * <p>
     * If the suffix ends with {@code .gz} or {@code .zip} the file will be compressed on rotation.
     * </p>
     *
     * @param suffix the suffix
     * @throws IllegalArgumentException if the suffix is not valid
     */
    public void setSuffix(String suffix) throws IllegalArgumentException {
        final SuffixRotator suffixRotator = SuffixRotator.parse(acc, suffix);
        final String dateSuffix = suffixRotator.getDatePattern();
        final DateTimeFormatter format = DateTimeFormatter.ofPattern(dateSuffix).withZone(timeZone.toZoneId());
        final int len = dateSuffix.length();
        Period period = Period.NEVER;
        for (int i = 0; i < len; i ++) {
            switch (dateSuffix.charAt(i)) {
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
                case '\'': while (dateSuffix.charAt(++i) != '\''); break;
                case 's':
                case 'S': throw new IllegalArgumentException("Rotating by second or millisecond is not supported");
            }
        }
        synchronized (outputLock) {
            this.format = format;
            this.period = period;
            this.suffixRotator = suffixRotator;
            final Instant now;
            final File file = getFile();
            if (file != null && file.lastModified() > 0) {
                now = Instant.ofEpochMilli(file.lastModified());
            } else {
                now = Instant.now();
            }
            calcNextRollover(now);
        }
    }

    /**
     * Returns the suffix to be used.
     *
     * @return the suffix to be used
     */
    protected final String getNextSuffix() {
        return nextSuffix;
    }

    /**
     * Returns the file rotator for this handler.
     *
     * @return the file rotator
     */
    SuffixRotator getSuffixRotator() {
        return suffixRotator;
    }

    private void rollOver() {
        try {
            final File file = getFile();
            if (file == null) {
                // no file is set; a direct output stream or writer was specified
                return;
            }
            // first, close the original file (some OSes won't let you move/rename a file that is open)
            setFileInternal(null);
            // next, rotate it
            suffixRotator.rotate(SecurityActions.getErrorManager(acc, this), file.toPath(), nextSuffix);
            // start new file
            setFileInternal(file);
        } catch (IOException e) {
            reportError("Unable to rotate log file", e, ErrorManager.OPEN_FAILURE);
        }
    }

    private void calcNextRollover(final Instant fromTime) {
        if (period == Period.NEVER) {
            nextRollover = Instant.MAX;
            return;
        }
        ZonedDateTime zdt = ZonedDateTime.ofInstant(fromTime, timeZone.toZoneId());
        nextSuffix = zdt.format(format);
        final Period period = this.period;
        // round up to the next field depending on the period
        switch (period) {
            default:
            case YEAR:
                zdt = zdt.truncatedTo(ChronoUnit.YEARS).plusYears(1);
                break;
            case MONTH:
                zdt = zdt.truncatedTo(ChronoUnit.MONTHS).plusYears(1);
                break;
            case WEEK:
                zdt = zdt.truncatedTo(ChronoUnit.WEEKS).plusWeeks(1);
                break;
            case DAY:
                zdt = zdt.truncatedTo(ChronoUnit.DAYS).plusDays(1);
                break;
            case HALF_DAY:
                zdt = zdt.truncatedTo(ChronoUnit.HALF_DAYS).plusHours(12);
                break;
            case HOUR:
                zdt = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1);
                break;
            case MINUTE:
                zdt = zdt.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
                break;
        }
        nextRollover = zdt.toInstant();
    }

    /**
     * Get the configured time zone for this handler.
     *
     * @return the configured time zone
     */
    public TimeZone getTimeZone() {
        return timeZone;
    }

    /**
     * Set the configured time zone for this handler.
     *
     * @param timeZone the configured time zone
     */
    public void setTimeZone(final TimeZone timeZone) {
        if (timeZone == null) {
            throw new NullPointerException("timeZone is null");
        }
        this.timeZone = timeZone;
    }

    private void setFileInternal(final File file) throws FileNotFoundException {
        // At this point we should have already checked the security required
        if (System.getSecurityManager() == null) {
            super.setFile(file);
        } else {
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                try {
                    super.setFile(file);
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }
                return null;
            }, acc);
        }
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
