/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class KubernetesVersionedArtifactConverter extends KubernetesArtifactConverter {
  static final KubernetesVersionedArtifactConverter INSTANCE =
      new KubernetesVersionedArtifactConverter();

  private final ObjectMapper objectMapper = new ObjectMapper();

  private KubernetesVersionedArtifactConverter() {}

  @Override
  public Artifact toArtifact(
      ArtifactProvider provider, KubernetesManifest manifest, @Nonnull String account) {
    String version = getVersion(provider, account, manifest);
    return Artifact.builder()
        .type(artifactType(manifest.getKind()))
        .name(manifest.getName())
        .location(manifest.getNamespace())
        .version(version)
        .reference(getDeployedName(manifest.getName(), version))
        .putMetadata("account", account)
        .build();
  }

  @Override
  public String getDeployedName(Artifact artifact) {
    return getDeployedName(artifact.getName(), artifact.getVersion());
  }

  private String getDeployedName(String name, String version) {
    return String.join("-", name, version);
  }

  private String getVersion(
      ArtifactProvider provider, @Nonnull String account, KubernetesManifest manifest) {
    ImmutableList<Artifact> priorVersions =
        provider.getArtifacts(
            artifactType(manifest.getKind()), manifest.getName(), manifest.getNamespace(), account);

    Optional<String> maybeVersion = findMatchingVersion(priorVersions, manifest);
    if (maybeVersion.isPresent()) {
      String version = maybeVersion.get();
      log.info("Manifest {} was already deployed at version {} - reusing.", manifest, version);
      return version;
    } else {
      return findGreatestUnusedVersion(priorVersions);
    }
  }

  private static OptionalInt parseVersion(@Nonnull String versionString) {
    if (!versionString.startsWith("v")) {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(versionString.substring(1)));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  private String findGreatestUnusedVersion(List<Artifact> priorVersions) {
    int maxTaken =
        priorVersions.stream()
            .map(Artifact::getVersion)
            .map(Strings::nullToEmpty)
            .map(KubernetesVersionedArtifactConverter::parseVersion)
            .filter(OptionalInt::isPresent)
            .mapToInt(OptionalInt::getAsInt)
            .filter(i -> i >= 0)
            .max()
            .orElse(-1);
    return String.format("v%03d", maxTaken + 1);
  }

  private Optional<String> findMatchingVersion(
      List<Artifact> priorVersions, KubernetesManifest manifest) {
    return priorVersions.stream()
        .filter(
            a ->
                getLastAppliedConfiguration(a)
                    .map(c -> c.nonMetadataEquals(manifest))
                    .orElse(false))
        .findFirst()
        .map(Artifact::getVersion);
  }

  private Optional<KubernetesManifest> getLastAppliedConfiguration(Artifact artifact) {
    Object rawLastAppliedConfiguration = artifact.getMetadata("lastAppliedConfiguration");

    if (rawLastAppliedConfiguration == null) {
      return Optional.empty();
    }

    try {
      KubernetesManifest manifest =
          objectMapper.convertValue(rawLastAppliedConfiguration, KubernetesManifest.class);
      return Optional.of(manifest);
    } catch (RuntimeException e) {
      log.warn("Malformed lastAppliedConfiguration entry in {}: ", artifact, e);
      return Optional.empty();
    }
  }
}
