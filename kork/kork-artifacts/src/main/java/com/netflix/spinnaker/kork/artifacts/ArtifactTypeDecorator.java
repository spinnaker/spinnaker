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

/**
 * Decorator that sets the type of an artifact. This class is used to modify the type of artifacts,
 * particularly for converting between embedded and remote artifact types.
 */
public class ArtifactTypeDecorator implements ArtifactDecorator {
  /** The MIME type to be applied to the artifact. */
  private final String type;

  /**
   * Constructs a decorator with the specified artifact type.
   *
   * @param type The predefined artifact type to use
   */
  public ArtifactTypeDecorator(ArtifactTypes type) {
    this.type = type.getMimeType();
  }

  /**
   * Constructs a decorator with a custom type string.
   *
   * @param type The MIME type string to use
   */
  public ArtifactTypeDecorator(String type) {
    this.type = type;
  }

  /**
   * Creates a decorator that converts an embedded artifact type to a remote artifact type.
   *
   * @param artifact The artifact whose type should be converted
   * @return A decorator that will apply the remote type to an artifact
   * @throws ArtifactStoreInvalidTypeException if the artifact's type doesn't start with the
   *     embedded prefix
   */
  public static ArtifactTypeDecorator toRemote(Artifact artifact) {
    return new ArtifactTypeDecorator(
        convertTo(
            artifact,
            ArtifactTypes.CommonAffixes.EMBEDDED.asString(),
            ArtifactTypes.CommonAffixes.REMOTE.asString()));
  }

  /**
   * Creates a decorator that converts a remote artifact type to an embedded artifact type.
   *
   * @param artifact The artifact whose type should be converted
   * @return A decorator that will apply the embedded type to an artifact
   * @throws ArtifactStoreInvalidTypeException if the artifact's type doesn't start with the remote
   *     prefix
   */
  public static ArtifactTypeDecorator toEmbedded(Artifact artifact) {
    return new ArtifactTypeDecorator(
        convertTo(
            artifact,
            ArtifactTypes.CommonAffixes.REMOTE.asString(),
            ArtifactTypes.CommonAffixes.EMBEDDED.asString()));
  }

  /**
   * Converts an artifact's type from one prefix to another.
   *
   * @param artifact The artifact whose type should be converted
   * @param from The prefix to convert from
   * @param to The prefix to convert to
   * @return The new type string with the prefix replaced
   * @throws ArtifactStoreInvalidTypeException if the artifact's type is null or doesn't start with
   *     the expected prefix
   */
  private static String convertTo(Artifact artifact, String from, String to) {
    String t = artifact.getType();
    if (t == null || !t.startsWith(from + "/")) {
      throw new ArtifactStoreInvalidTypeException(
          String.format("Artifact of type %s failed to convert from %s to %s", t, from, to));
    }

    return to + t.substring(from.length());
  }

  /**
   * Decorates an artifact builder by setting its type.
   *
   * @param builder The artifact builder to decorate
   * @return The decorated artifact builder with the type set
   */
  @Override
  public Artifact.ArtifactBuilder decorate(Artifact.ArtifactBuilder builder) {
    return builder.type(this.type);
  }
}
