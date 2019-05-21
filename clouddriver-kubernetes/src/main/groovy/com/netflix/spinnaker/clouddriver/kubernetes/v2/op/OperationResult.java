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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.RegistryUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OperationResult {
  private Map<String, Set<String>> manifestNamesByNamespace = new HashMap<>();
  private Set<KubernetesManifest> manifests = new HashSet<>();
  private Set<Artifact> createdArtifacts = new HashSet<>();
  private Set<Artifact> boundArtifacts = new HashSet<>();

  public void removeSensitiveKeys(
      KubernetesResourcePropertyRegistry propertyRegistry, String accountName) {
    manifests.forEach(m -> RegistryUtils.removeSensitiveKeys(propertyRegistry, accountName, m));
  }

  public OperationResult addManifest(KubernetesManifest manifest) {
    manifests.add(manifest);

    Set<String> addedNames =
        manifestNamesByNamespace.getOrDefault(manifest.getNamespace(), new HashSet<>());
    addedNames.add(manifest.getFullResourceName());
    manifestNamesByNamespace.put(manifest.getNamespace(), addedNames);
    return this;
  }

  public void merge(OperationResult other) {
    for (Map.Entry<String, Set<String>> entry : other.manifestNamesByNamespace.entrySet()) {
      Set<String> thisManifests =
          this.manifestNamesByNamespace.getOrDefault(entry.getKey(), new HashSet<>());
      thisManifests.addAll(entry.getValue());
      this.manifestNamesByNamespace.put(entry.getKey(), thisManifests);
    }

    this.manifests.addAll(other.manifests);
    this.createdArtifacts.addAll(other.createdArtifacts);
    this.boundArtifacts.addAll(other.boundArtifacts);
  }
}
