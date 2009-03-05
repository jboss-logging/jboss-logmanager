package org.jboss.logmanager;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * An actual logger instance.  This is the end-user interface into the logging system.
 */
public final class LoggerInstance extends Logger {

    /**
     * The named logger tree node.
     */
    private final LoggerNode loggerNode;

    /**
     * Construct a new instance of an actual logger.
     *
     * @param loggerNode the node in the named logger tree
     * @param name the fully-qualified name of this node
     */
    LoggerInstance(final LoggerNode loggerNode, final String name) {
        // Logger.getLogger(*) will set up the resource bundle for us, how kind
        super(name, null);
        // We maintain our own "level"
        super.setLevel(Level.ALL);
        this.loggerNode = loggerNode;
        setLevel(null);
    }

    // Filter mgmt

    /** {@inheritDoc} */
    public void setFilter(Filter newFilter) throws SecurityException {
        super.setFilter(newFilter);
    }

    /** {@inheritDoc} */
    public Filter getFilter() {
        return super.getFilter();
    }

    // Level mgmt

    /**
     * The actual level.  May only be modified when the logmanager's level change lock is held; in addition, changing
     * this field must be followed immediately by recursively updating the effective loglevel of the child tree.
     */
    private volatile Level level;
    /**
     * The effective level.  May only be modified when the logmanager's level change lock is held; in addition, changing
     * this field must be followed immediately by recursively updating the effective loglevel of the child tree.
     */
    private volatile int effectiveLevel = Level.INFO.intValue();

    /**
     * {@inheritDoc}  This implementation grabs a lock, so that only one thread may update the log level of any
     * logger at a time, in order to allow readers to never block (though there is a window where retrieving the
     * log level reflects an older effective level than the actual level).
     */
    public void setLevel(Level newLevel) throws SecurityException {
        final LogContext context = loggerNode.getContext();
        context.checkAccess();
        context.levelTreeLock.lock();
        try {
            final Level oldLevel = level;
            level = newLevel;
            if (newLevel != null) {
                effectiveLevel = newLevel.intValue();
            } else {
                final LoggerInstance parent = (LoggerInstance) getParent();
                if (parent == null) {
                    effectiveLevel = Level.INFO.intValue();
                } else {
                    effectiveLevel = parent.effectiveLevel;
                }
            }
            if (oldLevel != newLevel) {
                // our level changed, recurse down to children
                loggerNode.updateChildEffectiveLevel(effectiveLevel);
            }
        } finally {
            context.levelTreeLock.unlock();
        }
    }

    /**
     * Update the effective level if it is inherited from a parent.  Must only be called while the logmanager's level
     * change lock is held.
     *
     * @param newLevel the new effective level
     */
    void setEffectiveLevel(int newLevel) {
        if (level == null) {
            effectiveLevel = newLevel;
            loggerNode.updateChildEffectiveLevel(newLevel);
        }
    }

    /** {@inheritDoc} */
    public Level getLevel() {
        return level;
    }

    /** {@inheritDoc} */
    public boolean isLoggable(Level level) {
        if (true) return true;
        final int effectiveLevel = this.effectiveLevel;
        return effectiveLevel <= level.intValue() && effectiveLevel != Level.OFF.intValue();
    }

    // Handler mgmt

    /** {@inheritDoc} */
    public void addHandler(Handler handler) throws SecurityException {
        super.addHandler(handler);
    }

    /** {@inheritDoc} */
    public void removeHandler(Handler handler) throws SecurityException {
        super.removeHandler(handler);
    }

    /** {@inheritDoc} */
    public Handler[] getHandlers() {
        return super.getHandlers();
    }

    /** {@inheritDoc} */
    public synchronized void setUseParentHandlers(boolean useParentHandlers) {
        super.setUseParentHandlers(useParentHandlers);
    }

    /** {@inheritDoc} */
    public synchronized boolean getUseParentHandlers() {
        return super.getUseParentHandlers();
    }

    // Parent/child

    /** {@inheritDoc} */
    public Logger getParent() {
        return loggerNode.getParentLogger();
    }

    /**
     * <b>Not allowed.</b>  This method may never be called.
     * @throws SecurityException always
     */
    public void setParent(Logger parent) {
        throw new SecurityException("setParent() disallowed");
    }

    // Logger

    /** {@inheritDoc} */
    public void log(LogRecord record) {
        // defeat inferring class name
        record.setSourceClassName(null);
        try {
            super.log(record);
        } catch (VirtualMachineError e) {
            // VM errors should be sent back, but otherwise...
            throw e;
        } catch (Throwable t) {
            // ignore problems
        }
    }

    // GC

    /**
     * Perform finalization actions.  This amounts to clearing out the loglevel so that all children are updated
     * with the parent's effective loglevel.  As such, a lock is acquired from this method which might cause delays in
     * garbage collection.
     */
    protected void finalize() throws Throwable {
        try {
            // clear out level so that it spams out to all children
            setLevel(null);
        } finally {
            super.finalize();
        }
    }
}
