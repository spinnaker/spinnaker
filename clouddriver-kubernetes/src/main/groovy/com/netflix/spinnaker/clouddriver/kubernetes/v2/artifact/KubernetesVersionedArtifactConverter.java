/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesVersionedArtifactConverter extends KubernetesArtifactConverter {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Artifact toArtifact(
      ArtifactProvider provider, KubernetesManifest manifest, String account) {
    String type = getType(manifest);
    String name = manifest.getName();
    String location = manifest.getNamespace();
    String version = getVersion(provider, type, name, location, manifest);
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("account", account);
    return Artifact.builder()
        .type(type)
        .name(name)
        .location(location)
        .version(version)
        .reference(getDeployedName(name, version))
        .metadata(metadata)
        .build();
  }

  @Override
  public KubernetesCoordinates toCoordinates(Artifact artifact) {
    return KubernetesCoordinates.builder()
        .kind(getKind(artifact))
        .name(getDeployedName(artifact))
        .namespace(getNamespace(artifact))
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
      ArtifactProvider provider,
      String type,
      String name,
      String location,
      KubernetesManifest manifest) {
    List<Artifact> priorVersions = provider.getArtifacts(type, name, location);

    Optional<String> maybeVersion = findMatchingVersion(priorVersions, manifest);
    if (maybeVersion.isPresent()) {
      String version = maybeVersion.get();
      log.info("Manifest {} was already deployed at version {} - reusing.", manifest, version);
      return version;
    } else {
      return findGreatestUnusedVersion(priorVersions);
    }
  }

  private String findGreatestUnusedVersion(List<Artifact> priorVersions) {
    List<Integer> taken =
        priorVersions.stream()
            .map(Artifact::getVersion)
            .filter(Objects::nonNull)
            .filter(v -> v.startsWith("v"))
            .map(v -> v.substring(1))
            .map(
                v -> {
                  try {
                    return Integer.valueOf(v);
                  } catch (NumberFormatException e) {
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .filter(i -> i >= 0)
            .collect(Collectors.toList());

    taken.sort(Integer::compareTo);
    int sequence = 0;
    if (!taken.isEmpty()) {
      sequence = taken.get(taken.size() - 1) + 1;
    }

    // Match vNNN pattern until impossible
    if (sequence < 1000) {
      return String.format("v%03d", sequence);
    } else {
      return String.format("v%d", sequence);
    }
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
    if (artifact.getMetadata() == null) {
      return Optional.empty();
    }

    Object rawLastAppliedConfiguration = artifact.getMetadata().get("lastAppliedConfiguration");

    if (rawLastAppliedConfiguration == null) {
      return Optional.empty();
    }

    try {
      KubernetesManifest manifest =
          objectMapper.convertValue(rawLastAppliedConfiguration, KubernetesManifest.class);
      return Optional.of(manifest);
    } catch (Exception e) {
      log.warn("Malformed lastAppliedConfiguration entry in {}: ", artifact, e);
      return Optional.empty();
    }
  }
}
