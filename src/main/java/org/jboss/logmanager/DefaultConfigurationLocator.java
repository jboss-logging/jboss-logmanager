/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Inc., and individual contributors
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
            System.err.printf("Unable to read the logging configuration from '%s' (%s)%n", propLoc, e);
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
