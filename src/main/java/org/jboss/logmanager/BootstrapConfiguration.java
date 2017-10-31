/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.logmanager.handlers.FileHandler;
import org.wildfly.common.Assert;

/**
 * Allows a {@link LogContext} to accept messages while it's still being configured. Once the configuration is complete
 * the messages will be sent back through the log process in the order in which they came arrived. This API allows some
 * minimal configuration on how bootstrapping is handled.
 * <p>
 * Once bootstrapping is complete the {@link LogContext#configurationComplete()} must be invoked in order to trigger
 * the queued messages to be processed.
 * </p>
 * <p>
 * Bootstrapping is said to be enabled when this configuration is explicitly {@linkplain #create(boolean) created}
 * with {@code true} or the {@code org.jboss.logmanager.bootstrap.enabled} is set to {@code true}.
 * </p>
 * <p>
 * If enabled a {@linkplain Runtime#addShutdownHook(Thread) shutdown hook} is added which will log messages if the JVM
 * exists before the {@linkplain LogContext#configurationComplete() configuration is complete}. By default the messages
 * will be written to a {@link FileHandler}. This can be overridden by changing the
 * {@code org.jboss.logmanager.bootstrap.log.type} to {@code console} which will then write messages to a
 * {@link ConsoleHandler}. If configuring either results in an error messages will be written to {@link System#err}.
 * </p>
 * <p>
 * If the {@code org.jboss.logmanager.bootstrap.log.type} system property is not {@code console} or not defined the
 * {@code org.jboss.logmanager.bootstrap.log.file} system property can be used to override the file the messages will
 * be written to. If left undefined the default file will be {@code ./bootstrap}.
 * </p>
 * <table border="1" cellpadding="4">
 * <caption style="text-align: left; padding-bottom: 5px"><strong>Available Configuration Properties</strong></caption>
 * <thead>
 * <tr>
 * <th>System Property</th>
 * <th>Description</th>
 * <th>Allowed Values</th>
 * <th>Default Value</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td><code>org.jboss.logmanager.bootstrap.enabled</code></td>
 * <td>Whether or not bootstrapping should be enabled.</td>
 * <td><code>true</code> or <code>false</code></td>
 * <td><code>false</code></td>
 * </tr>
 * <tr>
 * <td><code>org.jboss.logmanager.bootstrap.level</code></td>
 * <td>The level the root logger should be set to when bootstrapping is enabled. Note that the level used here
 * will be set on the root logger. If another level is desired on the root logger it must be explicitly set.</td>
 * <td>Any valid {@linkplain Level level}.</td>
 * <td><code>INFO</code></td>
 * </tr>
 * <tr>
 * <td><code>org.jboss.logmanager.bootstrap.log.type</code></td>
 * <td>The type of the handler to use if the JVM exits before {@linkplain LogContext#configurationComplete()
 * configuration is complete}.</td>
 * <td><code>console</code> or <code>file</code></td>
 * <td><code>file</code></td>
 * </tr>
 * <tr>
 * <td><code>org.jboss.logmanager.bootstrap.log.file</code></td>
 * <td>If the <code>org.jboss.logmanager.bootstrap.log.type</code> is set to <code>file</code> the path to the
 * log file that should be used.</td>
 * <td>Any valid path. If the parent directories do not exist they will be created.</td>
 * <td><code>./bootstrap.log</code></td>
 * </tr>
 * <tr>
 * <td><code>org.jboss.logmanager.bootstrap.queue-size</code></td>
 * <td>The size of the queue for logged messages until the configuration.</td>
 * <td>Any valid integer.</td>
 * <td>10,000</td>
 * <tr>
 * <td><code>org.jboss.logmanager.bootstrap.calculate.caller</code></td>
 * <td>Whether or not the caller should be calculated when the message is queued. Calculating the caller is an
 * expensive operation. If it is known that the caller will not need to be calculated it is suggested to set
 * this value to <code>false</code>.</td>
 * <td><code>true</code> or <code>false</code></td>
 * <td><code>true</code></td>
 * </tr>
 * </tbody>
 * </table>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class BootstrapConfiguration {

    private static final boolean BOOTSTRAP_ENABLED = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
        @Override
        public Boolean run() {
            final String value = System.getProperty("org.jboss.logmanager.bootstrap.enabled", "false");
            return value.isEmpty() || Boolean.parseBoolean(value);
        }
    });

    private static final Level BOOTSTRAP_LEVEL = AccessController.doPrivileged(new PrivilegedAction<Level>() {
        @Override
        public Level run() {
            try {
                return Level.parse(System.getProperty("org.jboss.logmanager.bootstrap.level", "INFO"));
            } catch (Exception ignore) {
            }
            return Level.INFO;
        }
    });

    private static final File BOOTSTRAP_FAILED_LOG_FILE = AccessController.doPrivileged(new PrivilegedAction<File>() {
        @Override
        public File run() {
            try {
                return new File(System.getProperty("org.jboss.logmanager.bootstrap.log.file", "./bootstrap.log"));
            } catch (Exception ignore) {
            }
            return new File("./bootstrap.log");
        }
    });

    private static final String BOOTSTRAP_FAILED_LOG_TYPE = AccessController.doPrivileged(new PrivilegedAction<String>() {
        @Override
        public String run() {
            return System.getProperty("org.jboss.logmanager.bootstrap.log.type", "file");
        }
    });

    private static final int BOOTSTRAP_QUEUE_SIZE = AccessController.doPrivileged(new PrivilegedAction<Integer>() {
        @Override
        public Integer run() {
            try {
                return Integer.parseInt(System.getProperty("org.jboss.logmanager.bootstrap.queue-size", "10000"));
            } catch (Exception ignore) {
            }
            return 10000;
        }
    });

    private static final boolean CALCULATE_CALLER = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
        @Override
        public Boolean run() {
            final String value = System.getProperty("org.jboss.logmanager.bootstrap.calculate.caller", "true");
            return value.isEmpty() || Boolean.parseBoolean(value);
        }
    });

    private static final String PATTERN = "%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n";

    private final boolean enabled;

    private Level level;
    private Supplier<? extends Handler> handler;
    private int queueSize;
    private boolean isCalculateCaller;

    private BootstrapConfiguration(final boolean enabled) {
        this.enabled = enabled;
        level = BOOTSTRAP_LEVEL;
        queueSize = BOOTSTRAP_QUEUE_SIZE;
        isCalculateCaller = CALCULATE_CALLER;
    }

    /**
     * Creates a new bootstrap configuration that uses the {@code org.jboss.logmanager.bootstrap.enabled} system
     * property to determine whether or not bootstrapping is enabled.
     *
     * @return a new bootstrap configuration
     */
    public static BootstrapConfiguration create() {
        return new BootstrapConfiguration(BOOTSTRAP_ENABLED);
    }

    /**
     * Creates a new bootstrap configuration.
     *
     * @param enabled {@code true} if the bootstrap configuration should be used or {@code false} if bootstraping
     *                shouldn't be used
     *
     * @return a new bootstrap configuration
     */
    public static BootstrapConfiguration create(final boolean enabled) {
        return new BootstrapConfiguration(enabled);
    }

    /**
     * Checks whether or not bootstrapping has been enabled.
     *
     * @return {@code true} if bootstrapping has been enabled, otherwise {@code false}
     */
    public boolean isBootstrapEnabled() {
        return enabled;
    }

    /**
     * Indicates whether or not the caller should be calculated when messages are queued. The default is {@code true}.
     *
     * @return {@code true} if the caller should be calculated, otherwise {@code false}
     */
    public boolean isCalculateCaller() {
        return isCalculateCaller;
    }

    /**
     * Sets whether or not the caller should be calculated before adding the log record to the queue.
     * <p>
     * Note that calculating the caller is an expensive operation. If it is known that the caller will not be required
     * it is suggested to set this value to {@code false}.
     * </p>
     *
     * @param calculateCaller {@code true} if the caller should be calculated, otherwise {@code false}
     *
     * @return this configuration
     */
    public BootstrapConfiguration setCalculateCaller(final boolean calculateCaller) {
        isCalculateCaller = calculateCaller;
        return this;
    }

    /**
     * Returns a supplier for the handler to use for logging messages to if the JVM shuts down before
     * {@link LogContext#configurationComplete()} is invoked.
     *
     * @return the supplier for the handler
     */
    public Supplier<? extends Handler> getHandler() {
        if (this.handler == null) {
            return getDefaultHandler();
        }
        return handler;
    }

    /**
     * Sets the handler to use for logging messages to if the JVM shuts down before the
     * {@link LogContext#configurationComplete()} is invoked.
     *
     * @param handler the handler to use
     *
     * @return this configuration
     */
    public BootstrapConfiguration setHandler(final Handler handler) {
        Assert.checkNotNullParam("handler", handler);
        return setHandler(new Supplier<Handler>() {
            @Override
            public Handler get() {
                return handler;
            }
        });
    }

    /**
     * Sets the handler to use for logging messages to if the JVM shuts down before the
     * {@link LogContext#configurationComplete()} is invoked. Note the handler will lazily be initialized only if
     * the shutdown hook is invoked.
     *
     * @param handler the handler to use
     *
     * @return this configuration
     */
    public BootstrapConfiguration setHandler(final Supplier<? extends Handler> handler) {
        this.handler = Assert.checkNotNullParam("handler", handler);
        return this;
    }

    /**
     * A shortcut to supplying a {@link ConsoleHandler} which will be lazily loaded if the JVM shuts down before the
     * {@link LogContext#configurationComplete()} is invoked.
     *
     * @return this configuration
     */
    public BootstrapConfiguration useConsoleHandler() {
        return setHandler(new Supplier<Handler>() {
            @Override
            public Handler get() {
                return new ConsoleHandler(new PatternFormatter(PATTERN));
            }
        });
    }

    /**
     * A shortcut to supplying a {@link FileHandler} which will be lazily loaded if the JVM shuts down before the
     * {@link LogContext#configurationComplete()} is invoked.
     *
     * @param path the path to the file to write messages to
     *
     * @return this configuration
     */
    public BootstrapConfiguration useFileHandler(final String path) {
        return useFileHandler(new File(Assert.checkNotNullParam("path", path)));
    }

    /**
     * A shortcut to supplying a {@link FileHandler} which will be lazily loaded if the JVM shuts down before the
     * {@link LogContext#configurationComplete()} is invoked.
     *
     * @param path the path to the file to write messages to
     *
     * @return this configuration
     */
    public BootstrapConfiguration useFileHandler(final Path path) {
        return useFileHandler(Assert.checkNotNullParam("path", path).toFile());
    }

    /**
     * A shortcut to supplying a {@link FileHandler} which will be lazily loaded if the JVM shuts down before the
     * {@link LogContext#configurationComplete()} is invoked.
     *
     * @param path the path to the file to write messages to
     *
     * @return this configuration
     */
    public BootstrapConfiguration useFileHandler(final File path) {
        Assert.checkNotNullParam("path", path);
        return setHandler(new Supplier<Handler>() {
            @Override
            public Handler get() {
                try {
                    return new FileHandler(new PatternFormatter(PATTERN), path, true);
                } catch (FileNotFoundException e) {
                    StandardOutputStreams.printError(e, "Could not create file handler, defaulting to a console " +
                            "handler for message output");
                    return new ConsoleHandler(new PatternFormatter(PATTERN));
                }
            }
        });
    }

    /**
     * Returns the level used for the bootstrapped messages.
     *
     * @return the level to use
     */
    public Level getLevel() {
        return level == null ? BOOTSTRAP_LEVEL : level;
    }

    /**
     * Allows the default level of {@link Level#INFO} to be overridden. This can also be controlled by the
     * {@code org.jboss.logmanager.bootstrap.level} system property.
     * <p>
     * Note that the level used here will be set on the root logger. If a different root logging level is desired it
     * must be explicitly set before the {@link LogContext#configurationComplete()} is invoked.
     * </p>
     *
     * @param level the log level to use for bootstrapped messages or {@code null} to use the
     *              {@code org.jboss.logmanager.bootstrap.level} system property.
     *
     * @return this configuration
     */
    public BootstrapConfiguration setLogLevel(final Level level) {
        this.level = level;
        return this;
    }

    /**
     * Returns the maximum size of the queue for the bootstrapped log messages.
     *
     * @return the maximum queue size
     */
    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Sets the maximum size of the queue for bootstrapped log messages. If the size is less than or equal to 0 the
     * system property {@code org.jboss.logmanager.bootstrap.queue-size} will be used to determine the size. If the
     * system property is not set 10,000 will be used.
     *
     * @param queueSize the queue size
     *
     * @return this configuration
     */
    public BootstrapConfiguration setQueueSize(final int queueSize) {
        if (queueSize <= 0) {
            this.queueSize = BOOTSTRAP_QUEUE_SIZE;
        } else {
            this.queueSize = queueSize;
        }
        return this;
    }

    /**
     * If {@linkplain #isBootstrapEnabled() enabled} this will return a configured {@link Bootstrap}, otherwise
     * {@code null} will be returned.
     *
     * @param loggerNode the logger node to associate with the bootstrap loggers
     *
     * @return a configured bootstrap or {@code null} if bootstrapping isn't enabled
     */
    Bootstrap build(final LoggerNode loggerNode) {
        return new Bootstrap(loggerNode, this);
    }

    private Supplier<Handler> getDefaultHandler() {
        if ("console".equalsIgnoreCase(BOOTSTRAP_FAILED_LOG_TYPE)) {
            return new Supplier<Handler>() {
                @Override
                public Handler get() {
                    return new ConsoleHandler(new PatternFormatter(PATTERN));
                }
            };
        }
        return new Supplier<Handler>() {
            @Override
            public Handler get() {
                try {
                    return new FileHandler(new PatternFormatter(PATTERN), BOOTSTRAP_FAILED_LOG_FILE, true);
                } catch (FileNotFoundException e) {
                    StandardOutputStreams.printError(e, PATTERN);
                }
                return new ConsoleHandler(ConsoleHandler.Target.SYSTEM_ERR, new PatternFormatter(PATTERN));
            }
        };
    }
}
