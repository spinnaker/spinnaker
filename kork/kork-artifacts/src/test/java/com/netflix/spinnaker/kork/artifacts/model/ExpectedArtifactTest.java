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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class ExpectedArtifactTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    // This avoids needing to write out all null values in our expected JSON and is how the
    // objectMapper in orca/clouddriver are configured.
    objectMapper.setSerializationInclusion(Include.NON_NULL);
  }

  private static final JsonNodeFactory jsonFactory = JsonNodeFactory.instance;

  @Test
  void deserializeAllFields() throws IOException {
    ExpectedArtifact result =
        objectMapper.readValue(fullExpectedArtifactJson(), ExpectedArtifact.class);
    ExpectedArtifact expected = fullExpectedArtifact();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void serializeAllFields() throws IOException {
    String result = objectMapper.writeValueAsString(fullExpectedArtifact());
    String expected = fullExpectedArtifactJson();

    // Compare the parsed trees of the two results, which is agnostic to key order
    AssertionsForClassTypes.assertThat(objectMapper.readTree(result))
        .isEqualTo(objectMapper.readTree(expected));
  }

  @Test
  void roundTripSerialization() throws IOException {
    ExpectedArtifact artifact = fullExpectedArtifact();
    String json = objectMapper.writeValueAsString(artifact);
    ExpectedArtifact deserializedArtifact = objectMapper.readValue(json, ExpectedArtifact.class);
    assertThat(deserializedArtifact).isEqualTo(artifact);
  }

  @Test
  void checkEmbeddedStoredTypesMatch() {
    Artifact embeddedTypeArtifact =
        Artifact.builder().type(ArtifactTypes.EMBEDDED_BASE64.getMimeType()).build();

    Artifact storedTypeArtifact =
        Artifact.builder().type(ArtifactTypes.REMOTE_BASE64.getMimeType()).build();

    Artifact noMatchTypeArtifact = Artifact.builder().type("does-not-exist").build();

    ExpectedArtifact expectedArtifact =
        ExpectedArtifact.builder().matchArtifact(embeddedTypeArtifact).build();
    assertThat(expectedArtifact.matches(storedTypeArtifact)).isTrue();
    assertThat(expectedArtifact.matches(embeddedTypeArtifact)).isTrue();
    assertThat(expectedArtifact.matches(noMatchTypeArtifact)).isFalse();

    expectedArtifact = ExpectedArtifact.builder().matchArtifact(storedTypeArtifact).build();
    assertThat(expectedArtifact.matches(embeddedTypeArtifact)).isTrue();
    assertThat(expectedArtifact.matches(storedTypeArtifact)).isTrue();
    assertThat(expectedArtifact.matches(noMatchTypeArtifact)).isFalse();
  }

  private String fullExpectedArtifactJson() {
    return jsonFactory
        .objectNode()
        .put("id", "test")
        .put("usePriorArtifact", true)
        .put("useDefaultArtifact", false)
        // We're using valueToTree rather than writing out the serialization of the Artifact class
        // on the assumption that the serialization of Artifact is separately tested.
        .<ObjectNode>set(
            "matchArtifact",
            objectMapper.valueToTree(Artifact.builder().type("gcs/object").build()))
        .<ObjectNode>set(
            "boundArtifact",
            objectMapper.valueToTree(
                Artifact.builder().type("gcs/object").name("my-artifact").build()))
        .toString();
  }

  private ExpectedArtifact fullExpectedArtifact() {
    return ExpectedArtifact.builder()
        .id("test")
        .usePriorArtifact(true)
        .useDefaultArtifact(false)
        .matchArtifact(Artifact.builder().type("gcs/object").build())
        .boundArtifact(Artifact.builder().type("gcs/object").name("my-artifact").build())
        .build();
  }

  @ParameterizedTest
  @MethodSource("regexTestCases")
  void testRegexMatching(String expectedName, String incomingName, boolean result) {
    ExpectedArtifact expectedArtifact =
        ExpectedArtifact.builder()
            .id("test")
            .matchArtifact(Artifact.builder().name(expectedName).build())
            .build();
    Artifact incomingArtifact = Artifact.builder().name(incomingName).build();
    assertThat(expectedArtifact.matches(incomingArtifact)).isEqualTo(result);
  }

  private static Stream<Arguments> regexTestCases() {
    return Stream.of(
        Arguments.of("abc", "abcde", false),
        Arguments.of("abc.*", "abcde", true),
        Arguments.of("bc.*", "abcde", false),
        Arguments.of(".*bc.*", "abcde", true),
        Arguments.of("abcde$", "abcde", true),
        Arguments.of("^abcde$", "abcde", true),
        Arguments.of("abc", null, false),
        Arguments.of("abc", "", false),
        Arguments.of("", "abcde", true),
        Arguments.of(null, "abcde", true));
  }

  @ParameterizedTest
  @MethodSource("matchConstructors")
  void testRequiredMatches(Function<String, Artifact> supplier) {
    String matchString = "abcd";
    String noMatchString = "zzz";

    ExpectedArtifact expectedArtifact =
        ExpectedArtifact.builder().id("test").matchArtifact(supplier.apply(matchString)).build();

    assertThat(expectedArtifact.matches(supplier.apply(matchString))).isTrue();
    assertThat(expectedArtifact.matches(supplier.apply(noMatchString))).isFalse();
  }

  private static Stream<Arguments> matchConstructors() {
    return Stream.<Function<String, Artifact>>of(
            s -> Artifact.builder().type(s).build(),
            s -> Artifact.builder().name(s).build(),
            s -> Artifact.builder().version(s).build(),
            s -> Artifact.builder().location(s).build(),
            s -> Artifact.builder().reference(s).build())
        .map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("noMatchConstructors")
  void testAllowedNonMatches(Function<String, Artifact> supplier) {
    String matchString = "abcd";
    String noMatchString = "zzz";

    ExpectedArtifact expectedArtifact =
        ExpectedArtifact.builder().id("test").matchArtifact(supplier.apply(matchString)).build();

    assertThat(expectedArtifact.matches(supplier.apply(matchString))).isTrue();
    assertThat(expectedArtifact.matches(supplier.apply(noMatchString))).isTrue();
  }

  private static Stream<Arguments> noMatchConstructors() {
    return Stream.<Function<String, Artifact>>of(
            s -> Artifact.builder().provenance(s).build(),
            s -> Artifact.builder().uuid(s).build(),
            s -> Artifact.builder().artifactAccount(s).build())
        .map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("referenceCases")
  void testReferenceMatches(
      String matchReference, String otherReference, boolean shouldMatch, String caseName) {
    ExpectedArtifact expectedArtifact =
        ExpectedArtifact.builder()
            .id("test")
            .matchArtifact(
                Artifact.builder()
                    .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
                    .reference(matchReference)
                    .build())
            .build();

    assertThat(
            expectedArtifact.matches(
                Artifact.builder()
                    .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
                    .reference(otherReference)
                    .build()))
        .as(caseName)
        .isEqualTo(shouldMatch);
  }

  private static Stream<Arguments> referenceCases() {
    return Stream.of(
        Arguments.of("SGVsbG8gV29ybGQK", "SGVsbG8gV29ybGQK", true, "simple"),
        Arguments.of("SGVsbG8gV29ybGQK", null, false, "other reference as null"),
        Arguments.of("SGVsbG8gV29ybGQK", "", false, "other reference is empty"),
        Arguments.of("", "SGVsbG8gV29ybGQK", true, "match reference is empty"),
        Arguments.of(
            "H4sIAAAAAAAAA9VRy27TQBDF7ynWLVoUuEaiFRzdxEBDqyYMAaVKDZqqtRAwIkFYNzVZ3nLzKvl0jRJY9oX6xTj/L/vl58duJWZPbUnXz4qW2WfOc+3ek//Sznje/P/uUcE+ujb73i8gNSrDX7/6oZcAqPvRd38UaKfJX8ce+vOWHwQ18S2/qn4n9LWn5t0/7RSLQ7c7XW4v1Dh+lRGiey0m2sROZlybnYaf8T9/0GkYRcsJ1RoKU3Zfh3jPvM/2q3JkNnEhq+7G5JXx6M2",
            "H4sIAAAAAAAAA9VRy27TQBDF7ynWLVoUuEaiFRzdxEBDqyYMAaVKDZqqtRAwIkFYNzVZ3nLzKvl0jRJY9oX6xTj/L/vl58duJWZPbUnXz4qW2WfOc+3ek//Sznje/P/uUcE+ujb73i8gNSrDX7/6oZcAqPvRd38UaKfJX8ce+vOWHwQ18S2/qn4n9LWn5t0/7RSLQ7c7XW4v1Dh+lRGiey0m2sROZlybnYaf8T9/0GkYRcsJ1RoKU3Zfh3jPvM/2q3JkNnEhq+7G5JXx6M2",
            true,
            "all possible base64 characters"),
        Arguments.of("+++", "+++", true, "valid base64 but invalid regex"));
  }
}
