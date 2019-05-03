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

package org.jboss.logmanager.formatters;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.jboss.logmanager.Environment;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class FormattersTests {

    @BeforeClass
    public static void java8Only() {
        Assume.assumeFalse(Environment.isModularJvm());
    }

    @Test
    public void getJarName_jar() throws MalformedURLException {
        String classResourceName = "org/alib/foo/Bar.class";
        URL resource = new URL(null, "jar:file:/D:/Java/SomeDir/alib-3.14.jar!/" + classResourceName, new DummyURLStreamHandler());

        assertEquals(StackTraceFormatter.getJarName(resource, classResourceName), "alib-3.14.jar");
    }

    @Test
    public void getJarName_module() throws MalformedURLException {
        String classResourceName = "org/blib/foo/Bar.class";
        URL resource = new URL(null, "module:blib-3.14.jar", new DummyURLStreamHandler());

        assertEquals(StackTraceFormatter.getJarName(resource, classResourceName), "blib-3.14.jar");
    }

    @Test
    public void getJarName_vfs() throws MalformedURLException {
        String classResourceName = "org/clib/foo/Bar.class";
        URL resource = new URL(null, "vfs:/D:/Java/jboss-as-7.1.1.Final/standalone/deployments/myapp.war/WEB-INF/lib/clib-3.1.4.jar", new DummyURLStreamHandler());

        assertEquals(StackTraceFormatter.getJarName(resource, classResourceName), "clib-3.1.4.jar");
    }

    @Test
    public void getJarName_other1() throws MalformedURLException {
        String classResourceName = "org/dlib/foo/Bar.class";
        URL resource = new URL(null, "foo://bar/" + classResourceName, new DummyURLStreamHandler());

        assertEquals(StackTraceFormatter.getJarName(resource, classResourceName), "");
    }

    @Test
    public void getJarName_other2() throws MalformedURLException {
        String classResourceName = "org/elib/foo/Bar.class";
        URL resource = new URL(null, "foo://bar/quux/" + classResourceName, new DummyURLStreamHandler());

        assertEquals(StackTraceFormatter.getJarName(resource, classResourceName), "quux");
    }

    @Test
    public void getJarName_other3() throws MalformedURLException {
        String classResourceName = "org/flib/foo/Bar.class";
        URL resource = new URL(null, "foo://bar/baz/quux/" + classResourceName, new DummyURLStreamHandler());

        assertEquals(StackTraceFormatter.getJarName(resource, classResourceName), "quux");
    }

}

class DummyURLStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        throw new UnsupportedOperationException();
    }

}
