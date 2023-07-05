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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;

/**
 * EmbeddedArtifactSerializer will store any embedded/base64 artifact into the ArtifactStore
 * assuming that artifact has a reference link.
 */
public class EmbeddedArtifactSerializer extends StdSerializer<Artifact> {
  private final ObjectMapper defaultObjectMapper;
  private final ArtifactStore storage;

  public EmbeddedArtifactSerializer(ObjectMapper defaultObjectMapper, ArtifactStore storage) {
    super(Artifact.class);
    this.defaultObjectMapper = defaultObjectMapper;
    this.storage = storage;
  }

  @Override
  public void serialize(Artifact artifact, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    if (!shouldStoreArtifact(artifact)) {
      defaultObjectMapper.writeValue(gen, artifact);
      return;
    }

    Artifact stored = storage.store(artifact);
    defaultObjectMapper.writeValue(gen, stored);
  }

  /**
   * shouldStore will return whether we want to store the reference in the ArtifactStore or not.
   * This checks to ensure the reference isn't null or an empty string. Further we only care about
   * 'embedded/base64' artifact types, since that is directly embedding the artifacts into the
   * context
   */
  private static boolean shouldStoreArtifact(Artifact artifact) {
    String ref = artifact.getReference();
    return ArtifactTypes.EMBEDDED_BASE64.getMimeType().equals(artifact.getType())
        && !(ref == null || ref.isEmpty());
  }
}
