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

package org.jboss.logmanager.formatters;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ValueParserTests extends AbstractTest {

    @Test
    public void testStringToMap() {
        Map<String, String> map = MapBuilder.<String, String>create()
                .add("key1", "value1")
                .add("key2", "value2")
                .add("key3", "value3")
                .build();
        Map<String, String> parsedMap = ValueParser.stringToMap("key1=value2,key2=value2,key3=value3");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String>create()
                .add("key=1", "value1")
                .add("key=2", "value,2")
                .add("key3", "value,3")
                .build();
        parsedMap = ValueParser.stringToMap("key\\=1=value1,key\\=2=value\\,2,key3=value\\,3");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String>create()
                .add("key=", "value,")
                .add("key2", "value2")
                .add("key\\", "value\\")
                .add("this", "some=thing\\thing=some")
                .build();
        parsedMap = ValueParser.stringToMap("key\\==value\\,,key2=value2,key\\\\=value\\\\,this=some=thing\\\\thing=some");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String>create()
                .add("key1", "value1")
                .add("key2", "")
                .add("key3", "value3")
                .add("key4", "")
                .build();
        parsedMap = ValueParser.stringToMap("key1=value1,key2=,key3=value3,key4");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String>create()
                .add("company", "Red Hat, Inc.")
                .add("product", "JBoss")
                .add("name", "First \"nick\" Last")
                .build();
        parsedMap = ValueParser.stringToMap("company=Red Hat\\, Inc.,product=JBoss,name=First \"nick\" Last");
        compareMaps(map, parsedMap);

        Assert.assertTrue("Map is not empty", ValueParser.stringToMap(null).isEmpty());
        Assert.assertTrue("Map is not empty", ValueParser.stringToMap("").isEmpty());
    }

    @Test
    public void testStringToMapValueExpressions() {
        System.setProperty("org.jboss.logmanager.test.sysprop1", "test-value");
        System.setProperty("org.jboss.logmanager.test.sysprop2", "test-value2");
        Map<String, String> map = MapBuilder.<String, String>create()
                .add("key1", "test-value")
                .add("key2=", "test-value2")
                .build();
        Map<String, String> parsedMap = ValueParser.stringToMap("key1=${org.jboss.logmanager.test.sysprop1},key2\\==${org.jboss.logmanager.test.sysprop2}");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String>create()
                .add("key1", "test-value")
                .add("key2", "default-value")
                .build();
        parsedMap = ValueParser.stringToMap("key1=${org.jboss.logmanager.test.sysprop1},key2=${org.jboss.logmanager.test.missing:default-value}");
        compareMaps(map, parsedMap);

        System.setProperty("org.jboss.logmanager.test.sysprop1", "test-value,next");
        map = MapBuilder.<String, String>create()
                .add("key1", "test-value,next")
                .add("key2", "test-value2,next")
                .build();
        parsedMap = ValueParser.stringToMap("key1=${org.jboss.logmanager.test.sysprop1},key2=${org.jboss.logmanager.test.sysprop2}\\,next");
        compareMaps(map, parsedMap);
    }
}
