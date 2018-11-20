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

import java.io.IOException;
import java.io.InputStream;

/**
 * A configurator for a log manager or context.
 */
public interface Configurator {

    /**
     * Configure the logmanager.
     *
     * @param inputStream the input stream to read
     * @throws IOException if an error occurs
     */
    void configure(InputStream inputStream) throws IOException;

    /**
     * The attachment key of the chosen configurator, used to maintain a strong ref to any
     * configured properties.
     */
    Logger.AttachmentKey<Configurator> ATTACHMENT_KEY = new Logger.AttachmentKey<Configurator>();
}
