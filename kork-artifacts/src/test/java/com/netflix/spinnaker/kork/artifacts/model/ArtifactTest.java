/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.artifacts.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

final class ArtifactTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    // This avoids needing to write out all null values in our expected JSON and is how the
    // objectMapper in orca/clouddriver are configured.
    objectMapper.setSerializationInclusion(Include.NON_NULL);
  }

  // In some cases (particularly BakeRequest preparation), orca uses an ObjectMapper configured
  // to use snake_case. Ideally all code consuming serialized data with such an ObjectMapper would
  // also use an ObjectMapper configured to use snake_case, but this is not currently the case.
  // In order to avoid breaking those workflows, we need to ensure that the serialization and
  // deserialization of artifacts is consistent, regardless of the naming strategy.
  // If at some point we are sure that a consistently-configured ObjectMapper is used for all
  // serialization-deserialization paths, these tests can be removed.
  private static final ObjectMapper snakeObjectMapper = new ObjectMapper();

  static {
    // This avoids needing to write out all null values in our expected JSON and is how the
    // objectMapper in orca/clouddriver are configured.
    snakeObjectMapper.setSerializationInclusion(Include.NON_NULL);
    snakeObjectMapper.setPropertyNamingStrategy(new PropertyNamingStrategy.SnakeCaseStrategy());
  }

  private static final JsonNodeFactory jsonFactory = JsonNodeFactory.instance;

  @Test
  void deserializeAllFields() throws IOException {
    Artifact result = objectMapper.readValue(fullArtifactJson(), Artifact.class);
    Artifact expected = fullArtifact();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void deserializeAllFieldsSnake() throws IOException {
    Artifact result = snakeObjectMapper.readValue(fullArtifactJson(), Artifact.class);
    Artifact expected = fullArtifact();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void serializeAllFields() throws IOException {
    String result = objectMapper.writeValueAsString(fullArtifact());
    String expected = fullArtifactJson();

    // Compare the parsed trees of the two results, which is agnostic to key order
    AssertionsForClassTypes.assertThat(objectMapper.readTree(result))
        .isEqualTo(objectMapper.readTree(expected));
  }

  @Test
  void serializeAllFieldsSnake() throws IOException {
    String result = snakeObjectMapper.writeValueAsString(fullArtifact());
    String expected = fullArtifactJson();

    // Compare the parsed trees of the two results, which is agnostic to key order
    AssertionsForClassTypes.assertThat(objectMapper.readTree(result))
        .isEqualTo(objectMapper.readTree(expected));
  }

  @Test
  void roundTripSerialization() throws IOException {
    Artifact artifact = fullArtifact();
    String json = objectMapper.writeValueAsString(artifact);
    Artifact deserializedArtifact = objectMapper.readValue(json, Artifact.class);
    assertThat(deserializedArtifact).isEqualTo(artifact);
  }

  @Test
  void roundTripSnake() throws IOException {
    Artifact artifact = fullArtifact();
    String json = snakeObjectMapper.writeValueAsString(artifact);
    Artifact deserializedArtifact = objectMapper.readValue(json, Artifact.class);
    assertThat(deserializedArtifact).isEqualTo(artifact);
  }

  @Test
  void unknownKeysInMetadata() throws IOException {
    String json = jsonFactory.objectNode().put("name", "my-artifact").put("id", "123").toString();
    Artifact deserializedArtifact = objectMapper.readValue(json, Artifact.class);

    assertThat(deserializedArtifact.getName()).isEqualTo("my-artifact");
    assertThat(deserializedArtifact.getMetadata("id")).isEqualTo("123");
  }

  @Test
  void kindIsIgnored() throws IOException {
    String json =
        jsonFactory.objectNode().put("kind", "test").put("name", "my-artifact").toString();

    Artifact deserializedArtifact = objectMapper.readValue(json, Artifact.class);
    assertThat(deserializedArtifact.getMetadata("kind")).isNull();
  }

  @Test
  void toBuilderCopiesFields() {
    Artifact originalArtifact = Artifact.builder().name("my-artifact").type("my-type").build();

    Artifact newArtifact = originalArtifact.toBuilder().build();
    assertThat(newArtifact.getName()).isEqualTo("my-artifact");
    assertThat(newArtifact.getType()).isEqualTo("my-type");
  }

  @Test
  void toBuilderCopiesMetadata() {
    Artifact originalArtifact =
        Artifact.builder().putMetadata("abc", "def").putMetadata("mykey", "myval").build();

    Artifact newArtifact = originalArtifact.toBuilder().build();
    assertThat(newArtifact.getMetadata("abc")).isEqualTo("def");
    assertThat(newArtifact.getMetadata("mykey")).isEqualTo("myval");
  }

  @Test
  void equalIfMetadataEqual() {
    Artifact first = Artifact.builder().putMetadata("abc", "123").build();
    Artifact second = Artifact.builder().putMetadata("abc", "123").build();

    assertThat(first).isEqualTo(second);
  }

  @Test
  void notEqualIfMetadataNotEqual() {
    Artifact first = Artifact.builder().putMetadata("abc", "123").build();
    Artifact second = Artifact.builder().putMetadata("abc", "456").build();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void notEqualIfMetadataMissing() {
    Artifact first = Artifact.builder().putMetadata("abc", "123").build();
    Artifact second = Artifact.builder().build();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void notEqualIfExtraMetadata() {
    Artifact first = Artifact.builder().putMetadata("abc", "123").build();
    Artifact second =
        Artifact.builder().putMetadata("abc", "123").putMetadata("def", "456").build();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void serializeComplexMetadata() throws IOException {
    Artifact artifact =
        Artifact.builder().putMetadata("test", ImmutableMap.of("nested", "abc")).build();
    JsonNode expectedNode =
        jsonFactory.objectNode().set("test", jsonFactory.objectNode().put("nested", "abc"));

    String result = objectMapper.writeValueAsString(artifact);
    JsonNode resultNode = objectMapper.readTree(result).at("/metadata");
    AssertionsForClassTypes.assertThat(resultNode).isEqualTo(expectedNode);
  }

  @Test
  void deserializeComplexMetatdata() throws IOException {
    String json =
        jsonFactory
            .objectNode()
            .set("test", jsonFactory.objectNode().put("nested", "abc"))
            .toString();

    Artifact artifact = objectMapper.readValue(json, Artifact.class);
    Object testData = artifact.getMetadata("test");
    assertThat(testData).isEqualTo(ImmutableMap.of("nested", "abc"));
  }

  @Test
  void deserializeNullMetadata() throws IOException {
    String json = jsonFactory.objectNode().set("metadata", jsonFactory.nullNode()).toString();

    // Ensure that there is no exception reading an artifact with null metadata.
    Artifact artifact = objectMapper.readValue(json, Artifact.class);
    assertThat(artifact.getMetadata("abc")).isNull();
  }

  @Test
  void immutableMetadata() throws IOException {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("key1", "before");

    Artifact artifact = Artifact.builder().metadata(metadata).build();

    assertThat(artifact.getMetadata("key1")).isEqualTo("before");
    assertThat(artifact.getMetadata("key2")).isNull();

    metadata.put("key1", "after");
    metadata.put("key2", "something");

    assertThat(artifact.getMetadata("key1")).isEqualTo("before");
    assertThat(artifact.getMetadata("key2")).isNull();
  }

  @Test
  void putMetadataNullValue() throws IOException {
    Artifact artifact = Artifact.builder().putMetadata("test", null).build();
    assertThat(artifact.getMetadata("test")).isNull();
  }

  @Test
  void putMetadataMapNullValue() throws IOException {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("test", null);
    Artifact artifact = Artifact.builder().metadata(metadata).build();
    assertThat(artifact.getMetadata("test")).isNull();
  }

  @Test
  void serializePutMetadataNullValue() throws IOException {
    String result =
        objectMapper.writeValueAsString(Artifact.builder().putMetadata("test", null).build());

    // Compare the parsed trees of the two results, which is agnostic to key order
    AssertionsForClassTypes.assertThat(objectMapper.readTree(result))
        .isEqualTo(objectMapper.readTree(emptyArtifactJson()));
  }

  @Test
  void serializePutMetadataMapNullValue() throws IOException {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("test", null);
    String result = objectMapper.writeValueAsString(Artifact.builder().metadata(metadata).build());

    // Compare the parsed trees of the two results, which is agnostic to key order
    AssertionsForClassTypes.assertThat(objectMapper.readTree(result))
        .isEqualTo(objectMapper.readTree(emptyArtifactJson()));
  }

  @Test
  void deserializeNullMetadataValue() throws IOException {
    String json =
        jsonFactory
            .objectNode()
            .<ObjectNode>set("metadata", jsonFactory.objectNode().<ObjectNode>set("key", null))
            .toString();
    Artifact result = objectMapper.readValue(json, Artifact.class);
    assertThat(result.getMetadata("key")).isNull();
  }

  @Test
  void deserializeNullUnknownKey() throws IOException {
    String json = jsonFactory.objectNode().<ObjectNode>set("key", null).toString();
    Artifact result = objectMapper.readValue(json, Artifact.class);
    assertThat(result.getMetadata("key")).isNull();
  }

  private String fullArtifactJson() {
    return jsonFactory
        .objectNode()
        .put("type", "gcs/object")
        .put("customKind", true)
        .put("name", "my-artifact")
        .put("version", "3")
        .put("location", "somewhere")
        .put("reference", "https://artifact.test/my-artifact")
        .put("artifactAccount", "my-account")
        .put("provenance", "history")
        .put("uuid", "6b9a5d0b-5706-41da-b379-234c27971482")
        .<ObjectNode>set("metadata", jsonFactory.objectNode().put("test", "123"))
        .toString();
  }

  // Returns the serialization of an empty artifact. Fields that default to null are omitted by our
  // serialization config, while other fields (boolean, Map) serialize to their default values.
  private String emptyArtifactJson() {
    return jsonFactory
        .objectNode()
        .put("customKind", false)
        .<ObjectNode>set("metadata", jsonFactory.objectNode())
        .toString();
  }

  private Artifact fullArtifact() {
    return Artifact.builder()
        .type("gcs/object")
        .customKind(true)
        .name("my-artifact")
        .version("3")
        .location("somewhere")
        .reference("https://artifact.test/my-artifact")
        .artifactAccount("my-account")
        .provenance("history")
        .uuid("6b9a5d0b-5706-41da-b379-234c27971482")
        .putMetadata("test", "123")
        .build();
  }
}
