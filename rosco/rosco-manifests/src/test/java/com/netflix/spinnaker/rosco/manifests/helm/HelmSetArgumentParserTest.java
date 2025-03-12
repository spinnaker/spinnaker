/*
 * Copyright 2024 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.manifests.helm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for HelmSetArgumentParser, ensuring it correctly parses string inputs into structured maps.
 * This class includes tests for various input scenarios, including basic key-value pairs, nested
 * structures, lists, type inference, and error handling. The tests are inspired by Helm's own
 * parser tests, adapted to validate the Java implementation's compatibility with expected Helm
 * behavior. Reference to Helm's Go parser tests for comparison and validation: <a
 * href="https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser_test.go">Helm Go Parser
 * Tests</a>.
 */
public class HelmSetArgumentParserTest {

  private static Stream<Arguments> helmParseArgs() {

    return Stream.of(
        Arguments.of("name1=value1", Map.of("name1", "value1"), true),
        Arguments.of("name1=value1", Map.of("name1", "value1"), false),
        Arguments.of("long_int_string=1234567890", Map.of("long_int_string", "1234567890"), true),
        Arguments.of("long_int_string=1234567890", Map.of("long_int_string", 1234567890L), false),
        Arguments.of("boolean=true", Map.of("boolean", true), false),
        Arguments.of("boolean=true", Map.of("boolean", "true"), true),
        Arguments.of("is_null=null", mapOfEntries(entry("is_null", null)), false),
        Arguments.of("is_null=null", Map.of("is_null", "null"), true),
        Arguments.of("zero=0", Map.of("zero", "0"), true),
        Arguments.of("zero=0", Map.of("zero", 0L), false),
        Arguments.of("leading_zeros=00009", Map.of("leading_zeros", "00009"), true),
        Arguments.of("leading_zeros=00009", Map.of("leading_zeros", "00009"), false),
        Arguments.of(
            "name1=null,f=false,t=true", Map.of("name1", "null", "f", "false", "t", "true"), true),
        Arguments.of(
            "name1=null,f=false,t=true",
            mapOfEntries(entry("name1", null), entry("f", false), entry("t", true)),
            false),
        Arguments.of(
            "name1=value1,name2=value2", Map.of("name1", "value1", "name2", "value2"), true),
        Arguments.of(
            "name1=value1,name2=value2,", Map.of("name1", "value1", "name2", "value2"), true),
        Arguments.of("name1=,name2=value2", Map.of("name1", "", "name2", "value2"), true),
        Arguments.of(
            "name1=one\\,two,name2=three\\,four",
            Map.of("name1", "one,two", "name2", "three,four"),
            true),
        Arguments.of(
            "name1=one\\=two,name2=three\\=four",
            Map.of("name1", "one=two", "name2", "three=four"),
            true),
        Arguments.of("a=b\\", Map.of("a", "b"), true),
        Arguments.of(
            "name1=one two three,name2=three two one",
            Map.of("name1", "one two three", "name2", "three two one"),
            true),
        Arguments.of("outer.inner=value", Map.of("outer", Map.of("inner", "value")), true),
        Arguments.of(
            "outer.middle.inner=value",
            Map.of("outer", Map.of("middle", Map.of("inner", "value"))),
            true),
        Arguments.of(
            "outer.inner1=value,outer.inner2=value2",
            Map.of(
                "outer",
                Map.of(
                    "inner1", "value",
                    "inner2", "value2")),
            true),
        Arguments.of("name1.name2=", Map.of("name1", Map.of("name2", "")), true),
        Arguments.of("name1.name2=1,name1.name2=", Map.of("name1", Map.of("name2", "")), true),
        Arguments.of("name1.=", Map.of(), true),
        Arguments.of("name1=", Map.of("name1", ""), true),
        Arguments.of("name1={value1,value2}", Map.of("name1", List.of("value1", "value2")), true),
        Arguments.of("name1={value1,value2}.", Map.of("name1", List.of("value1", "value2")), true),
        Arguments.of(
            "name1={value1,value2},name2={value1,value2}",
            Map.of(
                "name1", List.of("value1", "value2"),
                "name2", List.of("value1", "value2")),
            true),
        Arguments.of(
            "name1.name2={value1,value2}",
            Map.of("name1", Map.of("name2", List.of("value1", "value2"))),
            true),
        Arguments.of("list[0]=foo", Map.of("list", List.of("foo")), true),
        Arguments.of("list[0].foo=bar", Map.of("list", List.of(Map.of("foo", "bar"))), true),
        Arguments.of(
            "list[0].foo=bar,list[0].hello=world",
            Map.of("list", List.of(Map.of("foo", "bar", "hello", "world"))),
            true),
        Arguments.of("list[0]=foo,list[1]=bar", Map.of("list", List.of("foo", "bar")), true),
        Arguments.of("list[0]=foo,list[1]=bar,", Map.of("list", List.of("foo", "bar")), true),
        Arguments.of("list[1]=foo,list[0]=bar,", Map.of("list", List.of("bar", "foo")), true),
        Arguments.of(".", Map.of(), true),
        Arguments.of("a[0].", Map.of("a", List.of()), true),
        Arguments.of("a[0][0].", Map.of("a", List.of(List.of())), true),
        Arguments.of(
            "list[0]=foo,list[3]=bar",
            new HashMap<String, Object>() {
              {
                put("list", Arrays.asList("foo", null, null, "bar"));
              }
            },
            true),
        Arguments.of("noval[0]", Map.of("noval", List.of()), true),
        Arguments.of("noval[0]=", Map.of("noval", List.of("")), true),
        Arguments.of("foo2[0]=1,foo2[1].", Map.of("foo2", List.of("1")), true),
        Arguments.of(
            "noval[0][1]={1,2},noval[0][0]={2,2}",
            Map.of("noval", List.of(List.of(List.of("2", "2"), List.of("1", "2")))),
            true),
        Arguments.of(
            "noval[0][1]={1,2},noval[0][0]={2,2}",
            Map.of("noval", List.of(List.of(List.of(2L, 2L), List.of(1L, 2L)))),
            false),
        Arguments.of(
            "noval[1][2]={a,b,c}",
            Map.of("noval", Arrays.asList(null, Arrays.asList(null, null, List.of("a", "b", "c")))),
            true),
        Arguments.of("nested[0][0]=a", Map.of("nested", List.of(List.of("a"))), true),
        Arguments.of(
            "nested[1][1]=a",
            Map.of("nested", Arrays.asList(null, Arrays.asList(null, "a"))),
            true),
        Arguments.of(
            "name1.name2[0].foo=bar,name1.name2[1].foo=bar",
            Map.of("name1", Map.of("name2", List.of(Map.of("foo", "bar"), Map.of("foo", "bar")))),
            true),
        Arguments.of(
            "name1.name2[1].foo=bar,name1.name2[0].foo=bar",
            Map.of("name1", Map.of("name2", List.of(Map.of("foo", "bar"), Map.of("foo", "bar")))),
            true),
        Arguments.of(
            "name1.name2[1].foo=bar",
            Map.of("name1", Map.of("name2", Arrays.asList(null, Map.of("foo", "bar")))),
            true),
        Arguments.of("key[0]=value,key=value", Map.of("key", "value"), true),
        Arguments.of("key1.key2=value,key1=value", Map.of("key1", "value"), true),
        Arguments.of("key=1.234", Map.of("key", "1.234"), true),
        Arguments.of("key=1.234", Map.of("key", "1.234"), false));
  }

  private static Stream<Arguments> helmParseArgsError() {
    return Stream.of(
        Arguments.of("name1=value1,,,,name2=value2,", "key  has no value (cannot end with ,)"),
        Arguments.of("name1,name2=", "key name1 has no value (cannot end with ,)"),
        Arguments.of("name1,name2=value2", "key name1 has no value (cannot end with ,)"),
        Arguments.of("name1,name2=value2\\", "key name1 has no value (cannot end with ,)"),
        Arguments.of("name1,name2", "key name1 has no value (cannot end with ,)"),
        Arguments.of("name1.=name2", "key map name1 has no value"),
        Arguments.of("name1.=,name1=name2", "key map name1 has no value"),
        Arguments.of("name1.name2", "key name2 has no value"),
        Arguments.of("name1.name2,name1.name3", "key name2 has no value (cannot end with ,)"),
        Arguments.of("name1={", "list must terminate with '}'"),
        Arguments.of("name1.name2={", "list must terminate with '}'"),
        Arguments.of("name1[0]={", "list must terminate with '}'"),
        Arguments.of("name1[0].name2={", "list must terminate with '}'"),
        Arguments.of("name1[0].name2[0]={", "list must terminate with '}'"),
        Arguments.of("name1.,name2", "key  has no value (cannot end with ,)"),
        Arguments.of("a[0][0]{", "unexpected data at end of array index {"),
        Arguments.of(
            "list[65537]=foo", "index of 65537 is greater than maximum supported index of 65536"),
        Arguments.of("list[0].foo=bar,list[-30].hello=world", "negative -30 index not allowed"),
        Arguments.of("list[0]=foo,list[-20]=bar", "negative -20 index not allowed"),
        Arguments.of("illegal[0]name.foo=bar", "unexpected data at end of array index name"),
        Arguments.of("noval[1][2]={a,b},c}", "key c} has no value"),
        Arguments.of(
            "illegal[0=1",
            "Error parsing index: Expected closing bracket ']', but encountered EOF"),
        Arguments.of("illegal[ab]=1", "Error parsing index: parsing 'ab': invalid syntax"),
        Arguments.of(" ", "key   has no value"),
        Arguments.of("a=1,a.b=1", "class java.lang.String cannot be cast to class java.util.Map"),
        Arguments.of("a=1,a[0]=1", "class java.lang.String cannot be cast to class java.util.List"),
        Arguments.of(
            "a[0]=1,a.b=1", "class java.util.ArrayList cannot be cast to class java.util.Map"),
        Arguments.of(
            "a.b=1,a[0]=1", "class java.util.HashMap cannot be cast to class java.util.List"),
        // Max limit is 30 for helm3 "v3.13.1". helm2 (v2.14.2)
        // doesn't seem to have a limit.
        Arguments.of(
            createNestedKey(31, "value"),
            "Value name nested level is greater than maximum supported nested level of 30"));
  }

  @ParameterizedTest
  @MethodSource("helmParseArgs")
  public void testParseSuccess(String input, Map<String, Object> expected, boolean stringValue)
      throws Exception {
    HelmSetArgumentParser helmSetArgumentParser = new HelmSetArgumentParser(input, stringValue);
    Map<String, Object> map = helmSetArgumentParser.parse();
    assertThat(map).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("helmParseArgsError")
  public void testParseError(String input, String errorMessage) {
    HelmSetArgumentParser helmSetArgumentParser = new HelmSetArgumentParser(input, true);
    assertThatThrownBy(helmSetArgumentParser::parse)
        .isInstanceOf(Exception.class)
        .hasMessageContaining(errorMessage);
  }

  private static String createNestedKey(int level, String value) {
    if (level < 0) {
      throw new IllegalArgumentException("Level must be at least 1");
    }

    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i <= level + 1; i++) {
      stringBuilder.append("name").append(i);
      if (i <= level) {
        stringBuilder.append(".");
      }
    }
    stringBuilder.append("=").append(value);
    return stringBuilder.toString();
  }

  @SafeVarargs
  private static <K, V> Map<K, V> mapOfEntries(Map.Entry<K, V>... entries) {
    Map<K, V> map = new HashMap<>();
    for (Map.Entry<K, V> entry : entries) {
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }

  private static <K, V> Map.Entry<K, V> entry(K key, V value) {
    return new HashMap.SimpleEntry<>(key, value);
  }
}
