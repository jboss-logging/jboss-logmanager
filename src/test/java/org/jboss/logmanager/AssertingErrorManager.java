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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.ErrorManager;

import org.junit.Assert;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AssertingErrorManager extends ErrorManager {

    private final int[] allowedCodes;

    private AssertingErrorManager() {
        this.allowedCodes = null;
    }

    private AssertingErrorManager(final int... allowedCodes) {
        this.allowedCodes = allowedCodes;
    }

    public static AssertingErrorManager of() {
        return new AssertingErrorManager();
    }

    public static AssertingErrorManager of(final int... allowedCodes) {
        return new AssertingErrorManager(allowedCodes);
    }

    @Override
    public void error(final String msg, final Exception ex, final int code) {
        if (notAllowed(code)) {
            final String codeStr;
            switch (code) {
                case CLOSE_FAILURE:
                    codeStr = "CLOSE_FAILURE";
                    break;
                case FLUSH_FAILURE:
                    codeStr = "FLUSH_FAILURE";
                    break;
                case FORMAT_FAILURE:
                    codeStr = "FORMAT_FAILURE";
                    break;
                case GENERIC_FAILURE:
                    codeStr = "GENERIC_FAILURE";
                    break;
                case OPEN_FAILURE:
                    codeStr = "OPEN_FAILURE";
                    break;
                case WRITE_FAILURE:
                    codeStr = "WRITE_FAILURE";
                    break;
                default:
                    codeStr = "INVALID (" + code + ")";
                    break;
            }
            try (
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw)
            ) {
                pw.printf("LogManager error of type %s: %s%n", codeStr, msg);
                if (ex != null) {
                    ex.printStackTrace(pw);
                }
                Assert.fail(sw.toString());
            } catch (IOException e) {
                // This shouldn't happen, but just fail if it does
                e.printStackTrace();
                Assert.fail(String.format("Failed to print error message: %s", e.getMessage()));
            }
        }
    }

    private boolean notAllowed(final int code) {
        if (allowedCodes != null) {
            for (int allowedCode : allowedCodes) {
                if (code == allowedCode) {
                    return false;
                }
            }
        }
        return true;
    }
}
