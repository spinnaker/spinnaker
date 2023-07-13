/*
 * Copyright 2023 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.s3.S3ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.stream.Stream;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class EmbeddedArtifactSerializerTest {
  @ParameterizedTest(name = "{index} {0}")
  @MethodSource("generateTestCase")
  public void serializeEmbeddedBase64Artifact_test(
      String name, String expectedJson, Artifact artifact, Artifact mockArtifact)
      throws IOException {
    ArtifactStore storage = Mockito.mock(S3ArtifactStore.class);
    when(storage.store(Mockito.any())).thenReturn(mockArtifact);

    EmbeddedArtifactSerializer serializer =
        new EmbeddedArtifactSerializer(new ObjectMapper(), storage);
    ObjectMapper objectMapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(Artifact.class, serializer);
    objectMapper.registerModule(module);

    String result = objectMapper.writeValueAsString(artifact);
    assertEquals(expectedJson, result);
  }

  private static Stream<Arguments> generateTestCase() {
    return Stream.of(
        Arguments.of(
            "simple",
            "{\"type\":\"remote/base64\",\"customKind\":false,\"name\":null,\"version\":null,\"location\":null,\"reference\":\"link\",\"metadata\":{},\"artifactAccount\":null,\"provenance\":null,\"uuid\":null}",
            Artifact.builder()
                .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
                .reference(Base64.encodeBase64String("foo".getBytes()))
                .build(),
            Artifact.builder()
                .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
                .reference("link")
                .build()),
        Arguments.of(
            "stored",
            "{\"type\":\"remote/base64\",\"customKind\":false,\"name\":null,\"version\":null,\"location\":null,\"reference\":\"link\",\"metadata\":{},\"artifactAccount\":null,\"provenance\":null,\"uuid\":null}",
            Artifact.builder()
                .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
                .reference("link")
                .build(),
            Artifact.builder()
                .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
                .reference("link")
                .build()),
        Arguments.of(
            "does-not-exist",
            "{\"type\":\"nonexistent-type\",\"customKind\":false,\"name\":null,\"version\":null,\"location\":null,\"reference\":\"Zm9v\",\"metadata\":{},\"artifactAccount\":null,\"provenance\":null,\"uuid\":null}",
            Artifact.builder()
                .type("nonexistent-type")
                .reference(Base64.encodeBase64String("foo".getBytes()))
                .build(),
            Artifact.builder()
                .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
                .reference("link")
                .build()));
  }
}
