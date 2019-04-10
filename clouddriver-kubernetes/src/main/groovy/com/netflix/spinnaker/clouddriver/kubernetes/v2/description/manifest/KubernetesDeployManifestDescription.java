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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesAtomicOperationDescription;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class KubernetesDeployManifestDescription extends KubernetesAtomicOperationDescription {
  @Deprecated
  private KubernetesManifest manifest;
  private List<KubernetesManifest> manifests;
  private Moniker moniker;
  private List<Artifact> requiredArtifacts;
  private List<Artifact> optionalArtifacts;
  private Boolean versioned;
  private Source source;
  private Artifact manifestArtifact;

  private boolean enableTraffic = true;
  private List<String> services;

  public enum Source {
    artifact,
    text
  }
}
