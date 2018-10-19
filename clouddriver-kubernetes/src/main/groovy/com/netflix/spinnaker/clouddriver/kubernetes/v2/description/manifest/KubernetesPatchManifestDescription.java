/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesAtomicOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class KubernetesPatchManifestDescription extends KubernetesAtomicOperationDescription {
  private String manifestName;
  private String location;

  // This will only be a portion of a full manifest so calls to some required fields can fail.
  // Using the KubernetesManifest type makes it simpler to reuse the ArtifactReplacement logic.
  // TODO: change Orca to only send a single manifest.
  private Object patchBody;
  private List<Artifact> requiredArtifacts;
  private List<Artifact> allArtifacts;
  private Artifact manifestArtifact;
  private KubernetesPatchOptions options;

  @JsonIgnore
  public KubernetesCoordinates getPointCoordinates() {
    Pair<KubernetesKind, String> parsedName = KubernetesManifest.fromFullResourceName(manifestName);

    return KubernetesCoordinates.builder()
      .namespace(location)
      .kind(parsedName.getLeft())
      .name(parsedName.getRight())
      .build();
  }
}

