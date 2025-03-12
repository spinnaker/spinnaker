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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArtifactDeserializerTest {
  private class InMemoryArtifactStore {
    private final Map<String, Artifact> storageMap = new HashMap<>();

    public InMemoryArtifactStore put(String id, Artifact artifact) {
      storageMap.put(id, artifact);
      return this;
    }

    public Artifact get(String id) {
      return storageMap.get(id);
    }
  }

  private class InMemoryArtifactStoreStorer implements ArtifactStoreStorer {
    public final InMemoryArtifactStore inMemoryArtifactStore;

    public InMemoryArtifactStoreStorer(InMemoryArtifactStore inMemoryArtifactStore) {
      this.inMemoryArtifactStore = inMemoryArtifactStore;
    }

    @Override
    public Artifact store(Artifact artifact) {
      inMemoryArtifactStore.put(artifact.getReference(), artifact);
      return artifact;
    }
  }

  private class InMemoryArtifactStoreGetter implements ArtifactStoreGetter {
    public final InMemoryArtifactStore inMemoryArtifactStore;

    public InMemoryArtifactStoreGetter(InMemoryArtifactStore inMemoryArtifactStore) {
      this.inMemoryArtifactStore = inMemoryArtifactStore;
    }

    @Override
    public Artifact get(ArtifactReferenceURI uri, ArtifactDecorator... decorator) {
      return inMemoryArtifactStore.get(uri.uri());
    }
  }

  @Test
  public void simpleDeserialization() throws IOException {
    String artifactJSON =
        "{\"type\":\"remote/base64\",\"customKind\":false,\"name\":null,\"version\":null,\"location\":null,\"reference\":\"ref://link\",\"metadata\":{},\"artifactAccount\":null,\"provenance\":null,\"uuid\":null}";
    String expectedReference = "foobar";
    Artifact expectedArtifact =
        Artifact.builder()
            .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
            .reference(expectedReference)
            .build();
    InMemoryArtifactStore inMemoryArtifactStore =
        new InMemoryArtifactStore().put("ref://link", expectedArtifact);
    ArtifactStore storage =
        new ArtifactStore(
            new InMemoryArtifactStoreGetter(inMemoryArtifactStore),
            new InMemoryArtifactStoreStorer(inMemoryArtifactStore));
    ArtifactDeserializer deserializer = new ArtifactDeserializer(new ObjectMapper(), storage);

    // We avoid using an object mapper here since the Artifact class has a
    // deserializer annotation which causes our deserializer to be ignored. So
    // rather than using a mixin and setting all that up, this is easier.
    JsonParser parser = new JsonFactory().createParser(artifactJSON);
    Artifact receivedArtifact = deserializer.deserialize(parser, null);
    assertNotNull(receivedArtifact);
    assertEquals(expectedArtifact.getReference(), receivedArtifact.getReference());
    assertEquals(ArtifactTypes.REMOTE_BASE64.getMimeType(), receivedArtifact.getType());
  }
}
