/*
 * Copyright 2019 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.client.model.v3;

import com.netflix.spinnaker.igor.build.model.GenericParameterDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

  @Test
  void getGenericParameterDefinitionListExtractsTravisEnvironmentVariables() {
    Config config = new Config(Map.of());
    config.setGlobalEnv(List.of("FOO=bar", "BAR=foo"));

    List<GenericParameterDefinition> genericParameterDefinitionList =
        config.getParameterDefinitionList();

    assertEquals(2, genericParameterDefinitionList.size());
    assertEquals("bar", genericParameterDefinitionList.get(0).getDefaultValue());
    assertEquals("BAR", genericParameterDefinitionList.get(1).getName());
  }

  @Test
  void getGenericParameterDefinitionListHandlesNull() {
    Config config = new Config(Map.of());
    config.setGlobalEnv(null);

    List<GenericParameterDefinition> genericParameterDefinitionList =
        config.getParameterDefinitionList();

    assertEquals(0, genericParameterDefinitionList.size());
  }

  @Test
  void getGenericParameterDefinitionListHandlesEqualsInValue() {
    Config config = new Config(Map.of());
    config.setGlobalEnv(List.of("FOO=\"foo=bar\""));

    List<GenericParameterDefinition> genericParameterDefinitionList =
        config.getParameterDefinitionList();

    assertEquals(1, genericParameterDefinitionList.size());
    assertEquals("\"foo=bar\"", genericParameterDefinitionList.get(0).getDefaultValue());
  }

  static Stream<Arguments> queryParametersTestData() {
    return Stream.of(
        Arguments.of(Map.of("FOO", "bar"), Map.of("matrix", "FOO=bar")),
        Arguments.of(Map.of("FOO", "bar", "BAR", "foo"), Map.of("matrix", "FOO=bar BAR=foo")),
        Arguments.of(null, null));
  }

  @ParameterizedTest
  @MethodSource("queryParametersTestData")
  void shouldHandleDifferentPermutationsOfQueryParameters(
      Map<String, String> queryParameters, Map<String, String> expectedEnv) {
    Config config = new Config(queryParameters);

    assertEquals(expectedEnv, config.getEnv());
  }

  static Stream<Arguments> envVarTypesTestData() {
    return Stream.of(
        Arguments.of(
            List.of("KEY_1=value 1", "KEY_2=value 2"),
            List.of("KEY_1=value 1", "KEY_2=value 2")),
        Arguments.of(
            "TF_INPUT=false "
                + "SOME_KEY=\"with spaces\" "
                + "ANOTHER_KEY=withoutspaces "
                + "lowercase-key=\"string with=equals sign\" "
                + "REGION=eu-west-1 "
                + "STACK_NAME=testing "
                + "A_USER=user@schibsted.com "
                + "A_PWD=[secure] "
                + "KEY_ID=\"SOMEKEYID\" "
                + "SOME_SECRET=[secure] "
                + "ROLE='arn:aws:iam::0123456789:role/MyRole' "
                + "KEY=whatabout=this\\\\ "
                + "KEY2=\"and=this\\\\\"",
            List.of(
                "TF_INPUT=false",
                "SOME_KEY=with spaces",
                "ANOTHER_KEY=withoutspaces",
                "lowercase-key=string with=equals sign",
                "REGION=eu-west-1",
                "STACK_NAME=testing",
                "A_USER=user@schibsted.com",
                "A_PWD=[secure]",
                "KEY_ID=SOMEKEYID",
                "SOME_SECRET=[secure]",
                "ROLE=arn:aws:iam::0123456789:role/MyRole",
                "KEY=whatabout=this\\\\",
                "KEY2=and=this\\\\")));
  }

  @ParameterizedTest
  @MethodSource("envVarTypesTestData")
  void shouldHandleDifferentEnvVarTypes(Object globalEnv, List<String> expectedGlobalEnv) {
    Config config = new Config();

    config.setGlobalEnv(globalEnv);

    assertEquals(expectedGlobalEnv, config.getGlobalEnv());
  }
}
