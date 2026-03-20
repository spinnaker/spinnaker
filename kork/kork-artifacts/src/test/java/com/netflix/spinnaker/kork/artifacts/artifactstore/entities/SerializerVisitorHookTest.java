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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationStorageFilter;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class SerializerVisitorHookTest {
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class MockContainer {
    private Map<Object, Object> values;
    private Set<Map<String, Object>> manifests;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class NullAnnotationCheck {
    private String value;
  }

  @Data
  @AllArgsConstructor
  public static class JsonIgnoreAnnotationCheck {
    private String value;
    @JsonIgnore private String ignored;
  }

  @BeforeAll
  public static void init() {
    AuthenticatedRequest.setApplication("foo");
    AuthenticatedRequest.setExecutionId("execution");
  }

  public static Stream<Arguments> serializeTestArgs() {
    return Stream.of(
        Arguments.of(Map.of("foo", "bar"), "{\"foo\":\"bar\"}"),
        Arguments.of(new HashMap<String, Integer>(Map.of("a", 0, "b", 1)), "{\"a\":0,\"b\":1}"),
        Arguments.of(
            Map.of("foo", "bar", "nested_map", Map.of("baz", "qux")),
            "{\"foo\":\"bar\",\"nested_map\":{\"baz\":\"qux\"}}"),
        Arguments.of(
            new MockContainer(
                Map.of(
                    "foo",
                    "bar",
                    "nested_map",
                    (Object) Map.of("nested_map_child", Map.of("f1", "v1"))),
                null),
            "{\"values\":{\"foo\":\"bar\",\"nested_map\":{\"nested_map_child\":{\"f1\":\"v1\"}}},\"manifests\":null}"),
        Arguments.of(
            Map.of("manifests", (Object) List.of(Map.of("manifest.field1", "manifest.value1"))),
            "{\"manifests\":[{\"customKind\":false,\"metadata\":{},\"reference\":\"ref://stored\",\"type\":\"remote/map/base64\"}]}"),
        Arguments.of(
            List.of(
                Map.of(
                    "manifests", (Object) List.of(Map.of("manifest.field1", "manifest.value1")))),
            "[{\"manifests\":[{\"customKind\":false,\"metadata\":{},\"reference\":\"ref://stored\",\"type\":\"remote/map/base64\"}]}]"),
        Arguments.of(
            new MockContainer(
                Map.of("field1", "value1", "field2", "value2"),
                Set.of(Map.of("manifest.field1", "manifest.value1"))),
            "{\"values\":{\"field1\":\"value1\",\"field2\":\"value2\"},\"manifests\":[{\"customKind\":false,\"metadata\":{},\"reference\":\"ref://stored\",\"type\":\"remote/map/base64\"}]}"),
        Arguments.of(new NullAnnotationCheck(null), "{}"),
        Arguments.of(new NullAnnotationCheck("foo"), "{\"value\":\"foo\"}"),
        Arguments.of(new JsonIgnoreAnnotationCheck("foo", "bar"), "{\"value\":\"foo\"}"));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("serializeTestArgs")
  public void serializeTest(Object value, String expectedJSON) {
    ArtifactStore storage = Mockito.mock(ArtifactStore.class);
    Mockito.when(storage.store(Mockito.any(), Mockito.any()))
        .thenReturn(
            Artifact.builder()
                .type(ArtifactTypes.REMOTE_MAP_BASE64.getMimeType())
                .reference("ref://stored")
                .build());
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();

    List<ArtifactHandler> mapHandlers = List.of(new ManifestMapStorageHandler(Map.of()));
    List<ArtifactHandler> listHandlers = List.of(new ManifestStorageCollectionHandler(Map.of()));
    ArtifactHandlerLists handlers =
        ArtifactHandlerLists.builder()
            .mapHandlers(mapHandlers)
            .collectionHandlers(listHandlers)
            .build();
    module.setSerializerModifier(new SerializerHookRegistry(storage, handlers));
    mapper.registerModule(module);
    // By default, object mapper does not sort the keys. To make this a little more deterministic,
    // we will sort our keys to make it easier to assert.
    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    String result = mapper.writeValueAsString(value);
    assertEquals(expectedJSON, result);
  }

  private static class CountHandler implements ArtifactStorageHandler {
    @Getter private int count = 0;

    @Override
    public boolean canHandle(Object v) {
      this.count += 1;
      return false;
    }

    @Override
    public <V> V handle(ArtifactStore store, V v, ObjectMapper objectMapper) {
      return v;
    }
  }

  @ParameterizedTest
  @MethodSource("countArgs")
  public void counts(Integer expected, Object m) {
    ArtifactStore storage = Mockito.mock(ArtifactStore.class);
    Mockito.when(storage.store(Mockito.any(), Mockito.any()))
        .thenReturn(
            Artifact.builder()
                .type(ArtifactTypes.REMOTE_MAP_BASE64.getMimeType())
                .reference("ref://stored")
                .build());
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();

    CountHandler handler = new CountHandler();
    ArtifactHandlerLists handlers =
        ArtifactHandlerLists.builder().mapHandlers(List.of(handler)).build();
    module.setSerializerModifier(new SerializerHookRegistry(storage, handlers));
    mapper.registerModule(module);
    try {
      mapper.writeValueAsString(m);
    } catch (JsonProcessingException e) {
      fail(e);
    }
    assertEquals(expected, handler.getCount());
  }

  public static Stream<Arguments> countArgs() {
    return Stream.of(
        Arguments.of(1, Map.of("foo", "bar")),
        Arguments.of(
            9,
            Map.of(
                "-0", Map.of("0", Map.of("a", "-a")),
                "-1", Map.of("1", Map.of("b", "-b")),
                "-2", Map.of("2", Map.of("c", "-c")),
                "-3", Map.of("3", Map.of("d", "-d")))),
        Arguments.of(
            9,
            new MockContainer(
                // 1st count
                Map.of(
                    "foo",
                    "bar",
                    "nested_map",
                    // 2nd, 3rd count
                    (Object) Map.of("nested_map_child", Map.of("f1", "v1"))),
                Set.of(
                    // 5th-6th count
                    Map.of("foo", Map.of("bar", Map.of("baz", 1))),
                    // 8th-9th count
                    Map.of("0", Map.of("1", Map.of("2", 1)))))));
  }

  /**
   * This test explicitly tests to ensure the serializer registry does not cache improper thing due
   * to filtering too early.
   */
  @Test
  public void ensureModifiersApplied() {
    AuthenticatedRequest.setApplication(null);
    ArtifactStore storage = Mockito.mock(ArtifactStore.class);
    Mockito.when(storage.store(Mockito.any(), Mockito.any()))
        .thenReturn(
            Artifact.builder()
                .type(ArtifactTypes.REMOTE_MAP_BASE64.getMimeType())
                .reference("ref://stored")
                .build());

    ApplicationStorageFilter filter = Mockito.mock(ApplicationStorageFilter.class);
    Mockito.when(filter.filter(Mockito.any())).thenReturn(false);
    Map<String, List<ApplicationStorageFilter>> filterMap =
        Map.of(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType(), List.of(filter));

    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    List<ArtifactHandler> mapHandlers = List.of(new ManifestMapStorageHandler(filterMap));
    List<ArtifactHandler> listHandlers = List.of(new ManifestStorageCollectionHandler(filterMap));
    ArtifactHandlerLists handlers =
        ArtifactHandlerLists.builder()
            .mapHandlers(mapHandlers)
            .collectionHandlers(listHandlers)
            .build();

    module.setSerializerModifier(new SerializerHookRegistry(storage, handlers));
    mapper.registerModule(module);

    Map<String, Object> m = Map.of("manifests", List.of(Map.of("foo", "bar")));
    try {
      String json = mapper.writeValueAsString(m);
      assertEquals("{\"manifests\":[{\"foo\":\"bar\"}]}", json);
    } catch (JsonProcessingException e) {
      fail(e);
    }

    AuthenticatedRequest.setApplication("app");
    try {
      String json = mapper.writeValueAsString(m);
      assertEquals(
          "{\"manifests\":[{\"type\":\"remote/map/base64\",\"customKind\":false,\"reference\":\"ref://stored\",\"metadata\":{}}]}",
          json);
    } catch (JsonProcessingException e) {
      fail(e);
    }
  }
}
