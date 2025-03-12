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
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public final class ResourceVersioner {
  private static final Logger log = LoggerFactory.getLogger(ResourceVersioner.class);

  private final ArtifactProvider artifactProvider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public ResourceVersioner(ArtifactProvider artifactProvider) {
    this.artifactProvider = Objects.requireNonNull(artifactProvider);
  }

  public OptionalInt getVersion(KubernetesManifest manifest, KubernetesCredentials credentials) {
    ImmutableList<Artifact> priorVersions =
        artifactProvider.getArtifacts(
            manifest.getKind(), manifest.getName(), manifest.getNamespace(), credentials);

    OptionalInt maybeVersion = findMatchingVersion(priorVersions, manifest);
    if (maybeVersion.isPresent()) {
      log.info(
          "Manifest {} was already deployed at version {} - reusing.",
          manifest,
          maybeVersion.getAsInt());
      return maybeVersion;
    } else {
      return OptionalInt.of(findGreatestUnusedVersion(priorVersions));
    }
  }

  public OptionalInt getLatestVersion(
      KubernetesManifest manifest, KubernetesCredentials credentials) {
    ImmutableList<Artifact> priorVersions =
        artifactProvider.getArtifacts(
            manifest.getKind(), manifest.getName(), manifest.getNamespace(), credentials);
    return findLatestVersion(priorVersions);
  }

  private static OptionalInt parseVersion(String versionString) {
    if (!versionString.startsWith("v")) {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(versionString.substring(1)));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  private int findGreatestUnusedVersion(List<Artifact> priorVersions) {
    OptionalInt latestVersion = findLatestVersion(priorVersions);
    if (latestVersion.isPresent()) {
      return latestVersion.getAsInt() + 1;
    }
    return 0;
  }

  private OptionalInt findLatestVersion(List<Artifact> priorVersions) {
    return extractVersions(priorVersions.stream()).max();
  }

  private OptionalInt findMatchingVersion(
      List<Artifact> priorVersions, KubernetesManifest manifest) {
    Stream<Artifact> matchingArtifacts =
        priorVersions.stream()
            .filter(
                a ->
                    getLastAppliedConfiguration(a)
                        .map(c -> c.nonMetadataEquals(manifest))
                        .orElse(false));

    return extractVersions(matchingArtifacts).findFirst();
  }

  private IntStream extractVersions(Stream<Artifact> artifacts) {
    return artifacts
        .map(Artifact::getVersion)
        .map(Strings::nullToEmpty)
        .map(ResourceVersioner::parseVersion)
        .filter(OptionalInt::isPresent)
        .mapToInt(OptionalInt::getAsInt)
        .filter(i -> i >= 0);
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
