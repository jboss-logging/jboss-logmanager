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

package org.jboss.logmanager;

import java.util.EnumMap;
import java.util.Map;

import org.jboss.logmanager.formatters.StructuredFormatter.Key;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyValuesTests extends MapTestUtils {

    @Test
    public void testStringToMap() {
        Map<String, String> map = MapBuilder.<String, String> create()
                .add("key1", "value1")
                .add("key2", "value2")
                .add("key3", "value3")
                .build();
        Map<String, String> parsedMap = PropertyValues.stringToMap("key1=value1,key2=value2,key3=value3");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String> create()
                .add("key=1", "value1")
                .add("key=2", "value,2")
                .add("key3", "value,3")
                .build();
        parsedMap = PropertyValues.stringToMap("key\\=1=value1,key\\=2=value\\,2,key3=value\\,3");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String> create()
                .add("key=", "value,")
                .add("key2", "value2")
                .add("key\\", "value\\")
                .add("this", "some=thing\\thing=some")
                .build();
        parsedMap = PropertyValues.stringToMap("key\\==value\\,,key2=value2,key\\\\=value\\\\,this=some=thing\\\\thing=some");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String> create()
                .add("key1", "value1")
                .add("key2", null)
                .add("key3", "value3")
                .add("key4", null)
                .build();
        parsedMap = PropertyValues.stringToMap("key1=value1,key2=,key3=value3,key4");
        compareMaps(map, parsedMap);

        map = MapBuilder.<String, String> create()
                .add("company", "Red Hat, Inc.")
                .add("product", "JBoss")
                .add("name", "First \"nick\" Last")
                .build();
        parsedMap = PropertyValues.stringToMap("company=Red Hat\\, Inc.,product=JBoss,name=First \"nick\" Last");
        compareMaps(map, parsedMap);

        Assertions.assertTrue(PropertyValues.stringToMap(null).isEmpty(), "Map is not empty");
        Assertions.assertTrue(PropertyValues.stringToMap("").isEmpty(), "Map is not empty");
    }

    @Test
    public void testStringToMapValueExpressions() {
        Map<String, String> map = MapBuilder.<String, String> create()
                .add("key1", "${org.jboss.logmanager.test.sysprop1}")
                .add("key2=", "${org.jboss.logmanager.test.sysprop2}")
                .build();
        Map<String, String> parsedMap = PropertyValues
                .stringToMap("key1=${org.jboss.logmanager.test.sysprop1},key2\\==${org.jboss.logmanager.test.sysprop2}");
        compareMaps(map, parsedMap);
    }

    @Test
    public void testStringToEnumMap() throws Exception {
        Map<Key, String> map = MapBuilder.<Key, String> create()
                .add(Key.EXCEPTION_CAUSED_BY, "cause")
                .add(Key.MESSAGE, "msg")
                .add(Key.HOST_NAME, "hostname")
                .build();
        EnumMap<Key, String> parsedMap = PropertyValues.stringToEnumMap(Key.class,
                "EXCEPTION_CAUSED_BY=cause,MESSAGE=msg,HOST_NAME=hostname");
        compareMaps(map, parsedMap);

        parsedMap = PropertyValues.stringToEnumMap(Key.class, "exception-caused-by=cause,message=msg,host-name=hostname");
        compareMaps(map, parsedMap);
    }

    @Test
    public void testMapToString() throws Exception {
        Map<String, String> map = MapBuilder.<String, String> create()
                .add("key1", "value1")
                .add("key2", "value2")
                .add("key3", "value3")
                .build();
        Assertions.assertEquals("key1=value1,key2=value2,key3=value3", PropertyValues.mapToString(map));

        map = MapBuilder.<String, String> create()
                .add("key=1", "value1")
                .add("key=2", "value,2")
                .add("key3", "value,3")
                .build();
        Assertions.assertEquals("key\\=1=value1,key\\=2=value\\,2,key3=value\\,3", PropertyValues.mapToString(map));

        map = MapBuilder.<String, String> create()
                .add("key=", "value,")
                .add("key2", "value2")
                .add("key\\", "value\\")
                .add("this", "some=thing\\thing=some")
                .build();
        Assertions.assertEquals("key\\==value\\,,key2=value2,key\\\\=value\\\\,this=some=thing\\\\thing=some",
                PropertyValues.mapToString(map));

        map = MapBuilder.<String, String> create()
                .add("key1", "value1")
                .add("key2", null)
                .add("key3", "value3")
                .add("key4", null)
                .build();
        Assertions.assertEquals("key1=value1,key2=,key3=value3,key4=", PropertyValues.mapToString(map));

        map = MapBuilder.<String, String> create()
                .add("company", "Red Hat, Inc.")
                .add("product", "JBoss")
                .add("name", "First \"nick\" Last")
                .build();
        Assertions.assertEquals("company=Red Hat\\, Inc.,product=JBoss,name=First \"nick\" Last",
                PropertyValues.mapToString(map));

        Assertions.assertTrue(PropertyValues.stringToMap(null).isEmpty(), "Expected an empty map");
        Assertions.assertTrue(PropertyValues.stringToMap("").isEmpty(), "Expected an empty map");
    }
}
