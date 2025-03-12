/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.manifest;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collection;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * When determining whether the deploy and patch manifest stages bound all required artifacts, the
 * artifacts in the list of required artifacts have an artifact account set while those in the list
 * we're trying to bind don't.
 *
 * <p>As the .equals function of Artifact includes the account in its comparison, this means that we
 * don't recognize the replaced artifacts as the ones we expected to replace and fail the stage.
 *
 * <p>As a temporary fix until we can refactor the artifact passing code to consistently include (or
 * not) account, or decide that account should always be excluded from Artifact.equals(), create a
 * class to hold the fields of Artifact that these two stages should use when deciding whether
 * artifacts are equal.
 */
@EqualsAndHashCode
@ToString
class ArtifactKey {
  private final String type;
  private final String name;
  private final String version;
  private final String location;
  private final String reference;

  private ArtifactKey(Artifact artifact) {
    this.type = artifact.getType();
    this.name = artifact.getName();
    this.version = artifact.getVersion();
    this.location = artifact.getLocation();
    this.reference = artifact.getReference();
  }

  @Nonnull
  static ArtifactKey fromArtifact(@Nonnull Artifact artifact) {
    return new ArtifactKey(artifact);
  }

  @Nonnull
  static ImmutableSet<ArtifactKey> fromArtifacts(@Nullable Collection<Artifact> artifacts) {
    if (artifacts == null) {
      return ImmutableSet.of();
    }
    return artifacts.stream()
        .filter(Objects::nonNull)
        .map(ArtifactKey::fromArtifact)
        .collect(toImmutableSet());
  }
}
