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

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.HashMap;
import java.util.Map;

final class KubernetesUnversionedArtifactConverter extends KubernetesArtifactConverter {
  static final KubernetesUnversionedArtifactConverter INSTANCE =
      new KubernetesUnversionedArtifactConverter();

  private KubernetesUnversionedArtifactConverter() {}

  @Override
  public Artifact toArtifact(
      ArtifactProvider provider, KubernetesManifest manifest, String account) {
    String type = getType(manifest);
    String name = manifest.getName();
    String location = manifest.getNamespace();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("account", account);
    return Artifact.builder()
        .type(type)
        .name(name)
        .location(location)
        .reference(name)
        .metadata(metadata)
        .build();
  }

  @Override
  public String getDeployedName(Artifact artifact) {
    return artifact.getName();
  }
}
