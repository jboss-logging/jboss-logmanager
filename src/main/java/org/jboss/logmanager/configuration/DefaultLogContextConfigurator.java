/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextConfigurator;
import org.jboss.logmanager.StandardOutputStreams;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.ConsoleHandler;

/**
 * A default log context configuration.
 * <p>
 * If the {@linkplain #configure(LogContext, InputStream) input stream} is {@code null} an attempt is made to find a
 * {@code logging.properties} file. If the file is not found, a {@linkplain java.util.ServiceLoader service loader} is
 * used to find the first implementation of a {@link LogContextConfigurator}. If that fails a default
 * {@link ConsoleHandler} is configured with the pattern {@code %d{yyyy-MM-dd'T'HH:mm:ssXXX} %-5p [%c] (%t) %s%e%n}.
 * </p>
 *
 * <p>
 * Locating the {@code logging.properties} happens in the following order:
 * <ul>
 * <li>The {@code logging.configuration} system property is checked</li>
 * <li>The current threads {@linkplain ClassLoader#getResourceAsStream(String)}  class loader} for a {@code logging.properties}</li>
 * <li>Finally {@link Class#getResourceAsStream(String)} is used to locate a {@code logging.properties}</li>
 * </ul>
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DefaultLogContextConfigurator implements LogContextConfigurator {

    @Override
    public void configure(final LogContext logContext, final InputStream inputStream) {
        final InputStream configIn = inputStream != null ? inputStream : findConfiguration();

        // Configure the log context based on a property file
        if (configIn != null) {
            final Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(configIn, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new RuntimeException("Failed to configure log manager with configuration file.", e);
            }
            PropertyConfigurator.configure(logContext, properties);
        } else {
            // Next check the service loader
            final Iterator<LogContextConfigurator> serviceLoader = ServiceLoader.load(LogContextConfigurator.class).iterator();
            if (serviceLoader.hasNext()) {
                serviceLoader.next().configure(logContext, inputStream);
            } else {
                // Configure a default console handler, pattern formatter and associated with the root logger
                final ConsoleHandler handler = new ConsoleHandler(new PatternFormatter("%d{yyyy-MM-dd'T'HH:mm:ssXXX} %-5p [%c] (%t) %s%e%n"));
                handler.setLevel(Level.INFO);
                handler.setAutoFlush(true);
                final Logger rootLogger = logContext.getLogger("");
                rootLogger.setLevel(Level.INFO);
                rootLogger.addHandler(handler);
            }
        }
    }

    private static InputStream findConfiguration() {
        final String propLoc = System.getProperty("logging.configuration");
        if (propLoc != null) try {
            return new URL(propLoc).openStream();
        } catch (IOException e) {
            StandardOutputStreams.printError("Unable to read the logging configuration from '%s' (%s)%n", propLoc, e);
        }
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) try {
            final InputStream stream = tccl.getResourceAsStream("logging.properties");
            if (stream != null) return stream;
        } catch (Exception ignore) {
        }
        return DefaultLogContextConfigurator.class.getResourceAsStream("logging.properties");
    }
}
