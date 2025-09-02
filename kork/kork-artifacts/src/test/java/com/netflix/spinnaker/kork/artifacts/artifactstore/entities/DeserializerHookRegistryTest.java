/*
 * Copyright 2025 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.util.Base64;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class DeserializerHookRegistryTest {
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  private static class MockClass {
    private String stringValue;
    private Integer intValue;
    private Float floatValue;
    private Boolean boolValue;
    private List<String> listValue;
    private Integer[] intArrayValue;
    private NestedMockClass child;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  private static class NestedMockClass {
    private String childValue;
  }

  @Data
  private static class JsonCreatorAnnotationCheck {
    private final String id;

    @JsonCreator
    public JsonCreatorAnnotationCheck(@JsonProperty("name") String id) {
      this.id = id;
    }
  }

  public static Stream<Arguments> testExpansionArgs() {
    return Stream.of(
        Arguments.of(
            """
                    {
                        "string": "parent",
                        "int": 1,
                        "float": 3.14,
                        "bool": true,
                        "object": {
                            "string": "child",
                            "int": 2,
                            "float": 2.58,
                            "bool": false
                        },
                        "array_simple": [0, 1, 2],
                        "array_complex": [
                            {
                                "elem": 0,
                                "name": "array_0"
                            },
                            {
                                "elem": 1,
                                "name": "array_1"
                            },
                            {
                                "elem": 2,
                                "name": "array_2"
                            }
                        ]
                    }
                    """,
            Map.of(
                "string",
                "parent",
                "int",
                1,
                "float",
                3.14,
                "bool",
                true,
                "object",
                Map.of("string", "child", "int", 2, "float", 2.58, "bool", false),
                "array_simple",
                List.of(0, 1, 2),
                "array_complex",
                List.of(
                    Map.of("elem", 0, "name", "array_0"),
                    Map.of("elem", 1, "name", "array_1"),
                    Map.of("elem", 2, "name", "array_2")))),
        Arguments.of(
            """
                   {
                    "stringValue": "foo",
                    "intValue": 5,
                    "floatValue": 3.14,
                    "boolValue": true,
                    "listValue": ["v1","v2","v3"],
                    "intArrayValue": [3,2,1],
                    "child": {
                        "childValue": "c1"
                    }
                   }
                   """,
            new MockClass(
                "foo",
                5,
                3.14f,
                true,
                List.of("v1", "v2", "v3"),
                new Integer[] {3, 2, 1},
                new NestedMockClass("c1"))),
        Arguments.of(
            """
                 {
                     "name": "foo"
                 }
                """,
            new JsonCreatorAnnotationCheck("foo")),
        Arguments.of(
            """
                     {
                         "manifests": [
                            {
                              "type": "remote/map/base64",
                              "reference": "ref://application/hash"
                            }
                         ]
                     }
                    """,
            Map.of("manifests", List.of(Map.of("hello", "world!")))));
  }

  @ParameterizedTest
  @MethodSource("testExpansionArgs")
  public void testExpansion(String json, Object expectedResult) throws Exception {
    ArtifactStore store = Mockito.mock(ArtifactStore.class);
    Mockito.when(store.get(Mockito.any(), Mockito.any()))
        .thenReturn(
            Artifact.builder()
                .type(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType())
                .reference(Base64.encodeAsString("{\"hello\": \"world!\"}".getBytes()))
                .build());

    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    List<ArtifactHandler> handlers = List.of(new ExpandToMapHandler());
    module.setDeserializerModifier(
        new DeserializerHookRegistry(
            store, ArtifactHandlerLists.builder().mapHandlers(handlers).build()));
    mapper.registerModule(module);
    Object result = mapper.readValue(json, expectedResult.getClass());
    assertEquals(expectedResult, result);
  }
}
