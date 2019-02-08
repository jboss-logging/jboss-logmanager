/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is required to be separate from the {@link JDKSpecific} helper. It's specifically required for WildFly embedded
 * as the {@link JDKSpecific} initializes JBoss Modules which could cause issues if it's initialized too early. This
 * avoids the early initialization.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class Jvm {
    private static final boolean MODULAR_JVM;

    static {

        // Get the current Java version and determine, by JVM version level, if this is a modular JDK
        final String value = AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getProperty("java.specification.version");
            }
        });
        // Shouldn't happen, but we'll assume we're not a modular environment
        boolean modularJvm = false;
        if (value != null) {
            final Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(value);
            if (matcher.find()) {
                modularJvm = Integer.parseInt(matcher.group(1)) >= 9;
            }
        }
        MODULAR_JVM = modularJvm;
    }

    /**
     * Determines whether or not this is a modular JVM. The version of the {@code java.specification.version} is checked
     * to determine if the version is greater than or equal to 9. This is required to disable specific features/hacks
     * for older JVM's when the log manager is loaded on the boot class path which doesn't support multi-release JAR's.
     *
     * @return {@code true} if determined to be a modular JVM, otherwise {@code false}
     */
    static boolean isModular() {
        return MODULAR_JVM;
    }
}
