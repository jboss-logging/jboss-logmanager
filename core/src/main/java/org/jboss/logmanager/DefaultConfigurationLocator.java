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

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

/**
 * A configuration locator which looks for a {@code logging.properties} file in the class path, allowing the location
 * to be overridden via a URL specified in the {@code logging.configuration} system property.
 */
public final class DefaultConfigurationLocator implements ConfigurationLocator {

    /** {@inheritDoc} */
    public InputStream findConfiguration() throws IOException {
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
        } catch (Exception e) {
        }
        return getClass().getResourceAsStream("logging.properties");
    }
}
