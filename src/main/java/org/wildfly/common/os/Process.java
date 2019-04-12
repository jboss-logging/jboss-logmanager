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

package org.wildfly.common.os;

import static java.security.AccessController.doPrivileged;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Process {
    private static final long processId;
    private static final String processName;

    static {
        Object[] array = doPrivileged(new GetProcessInfoAction());
        processId = ((Long) array[0]).longValue();
        processName = (String) array[1];
    }

    private Process() {
    }

    /**
     * Get the name of this process.  If the process name is not known, then "&lt;unknown&gt;" is returned.
     *
     * @return the process name (not {@code null})
     */
    public static String getProcessName() {
        return processName;
    }

    /**
     * Get the ID of this process.  This is the operating system specific PID.  If the PID cannot be determined,
     * -1 is returned.
     *
     * @return the ID of this process, or -1 if it cannot be determined
     */
    public static long getProcessId() {
        return processId;
    }
}
