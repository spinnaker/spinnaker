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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * ArtifactDeserializer is a custom deserializer that will allow for artifacts to be fetched from
 * some artifact store as long as the referenceLink field is set and the reference field is null.
 */
public class ArtifactDeserializer extends StdDeserializer<Artifact> {
  private final ObjectMapper defaultObjectMapper;
  private final ArtifactStore storage;

  public ArtifactDeserializer(
      @Qualifier(value = "artifactObjectMapper") ObjectMapper defaultObjectMapper,
      ArtifactStore storage) {
    super(Artifact.class);
    this.defaultObjectMapper = defaultObjectMapper;
    this.storage = storage;
  }

  @Override
  public Artifact deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
    Artifact artifact = defaultObjectMapper.readValue(parser, Artifact.class);
    if (ArtifactTypes.REMOTE_BASE64.getMimeType().equals(artifact.getType())) {
      return storage.get(
          ArtifactReferenceURI.parse(artifact.getReference()),
          new ArtifactMergeReferenceDecorator(artifact));
    }

    return artifact;
  }

  /**
   * ArtifactMergeReferenceDecorator is used to take some artifact and replace its reference with
   * the reference from another artifact.
   */
  public static class ArtifactMergeReferenceDecorator implements ArtifactDecorator {
    private final Artifact artifactToCopy;

    private ArtifactMergeReferenceDecorator(Artifact artifactToCopy) {
      this.artifactToCopy = artifactToCopy;
    }

    @Override
    public Artifact.ArtifactBuilder decorate(Artifact.ArtifactBuilder builder) {
      Artifact retrieved = builder.build();
      return artifactToCopy.toBuilder().reference(retrieved.getReference());
    }
  }
}
