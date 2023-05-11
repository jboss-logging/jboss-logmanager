package org.jboss.logmanager.errormanager;

import java.util.logging.Handler;

import org.jboss.logmanager.ExtErrorManager;

import io.smallrye.common.constraint.Assert;

/**
 * An error manager which publishes errors to a handler.
 */
public final class HandlerErrorManager extends ExtErrorManager {
    private static final ThreadLocal<Boolean> handlingError = new ThreadLocal<>();

    private final Handler handler;

    /**
     * Construct a new instance.
     *
     * @param handler the handler to set (must not be {@code null})
     */
    public HandlerErrorManager(final Handler handler) {
        Assert.checkNotNullParam("handler", handler);
        this.handler = handler;
    }

    public void error(final String msg, final Exception ex, final int code) {
        if (handlingError.get() != Boolean.TRUE) {
            handlingError.set(Boolean.TRUE);
            try {
                handler.publish(errorToLogRecord(msg, ex, code));
            } finally {
                handlingError.remove();
            }
        }
    }
}
