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
package com.netflix.spinnaker.kork.artifacts;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions.ArtifactStoreInvalidTypeException;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

public class ArtifactTypeDecorator implements ArtifactDecorator {
  private final String type;

  public ArtifactTypeDecorator(ArtifactTypes type) {
    this.type = type.getMimeType();
  }

  public ArtifactTypeDecorator(String type) {
    this.type = type;
  }

  public static ArtifactTypeDecorator toRemote(Artifact artifact) {
    return new ArtifactTypeDecorator(
        convertTo(
            artifact,
            ArtifactTypes.CommonAffixes.EMBEDDED.asString(),
            ArtifactTypes.CommonAffixes.REMOTE.asString()));
  }

  public static ArtifactTypeDecorator toEmbedded(Artifact artifact) {
    return new ArtifactTypeDecorator(
        convertTo(
            artifact,
            ArtifactTypes.CommonAffixes.REMOTE.asString(),
            ArtifactTypes.CommonAffixes.EMBEDDED.asString()));
  }

  private static String convertTo(Artifact artifact, String from, String to) {
    String t = artifact.getType();
    if (t == null || !t.startsWith(from + "/")) {
      throw new ArtifactStoreInvalidTypeException(
          String.format("Artifact of type %s failed to convert from %s to %s", t, from, to));
    }

    return to + t.substring(from.length());
  }

  @Override
  public Artifact.ArtifactBuilder decorate(Artifact.ArtifactBuilder builder) {
    return builder.type(this.type);
  }
}
