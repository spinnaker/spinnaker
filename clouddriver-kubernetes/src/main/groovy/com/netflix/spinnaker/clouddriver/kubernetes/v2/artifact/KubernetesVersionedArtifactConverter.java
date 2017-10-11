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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesV2ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class KubernetesVersionedArtifactConverter extends KubernetesArtifactConverter {
  @Autowired
  KubernetesV2ArtifactProvider artifactProvider;

  @Override
  public Artifact toArtifact(KubernetesManifest manifest) {
    String type = getType(manifest);
    String name = manifest.getName();
    String location = manifest.getNamespace();
    String version = getVersion(type, name, location);
    return Artifact.builder()
        .type(type)
        .name(name)
        .location(location)
        .version(version)
        .build();
  }

  @Override
  public KubernetesCoordinates toCoordinates(Artifact artifact) {
    return KubernetesCoordinates.builder()
        .apiVersion(getApiVersion(artifact))
        .kind(getKind(artifact))
        .name(getDeployedName(artifact))
        .namespace(getNamespace(artifact))
        .build();
  }

  @Override
  public String getDeployedName(Artifact artifact) {
    return artifact.getName() + "-" + artifact.getVersion();
  }

  private String getVersion(String type, String name, String location) {
    List<Artifact> priorVersions = artifactProvider.getArtifacts(type, name, location);

    List<Integer> taken = priorVersions.stream()
        .map(Artifact::getVersion)
        .filter(Objects::nonNull)
        .filter(v -> v.startsWith("v"))
        .map(v -> v.substring(1))
        .map(v -> {
          try {
            return Integer.valueOf(v);
          } catch (NumberFormatException e) {
            return null;
          }
        } )
        .filter(Objects::nonNull)
        .filter(i -> i >= 0)
        .collect(Collectors.toList());

    taken.sort(Integer::compareTo);

    int attempt = 0;
    for (Integer exists : taken) {
      if (attempt == exists) {
        attempt++;
      } else {
        break;
      }
    }

    // omit 1000 artifact restriction because it's silly (vNNN)
    return String.format("v%d", attempt);
  }
}
