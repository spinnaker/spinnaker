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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesAtomicOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KubernetesDeployManifestDescription extends KubernetesAtomicOperationDescription {
  @Deprecated private KubernetesManifest manifest;
  private List<KubernetesManifest> manifests;
  private Moniker moniker;
  private List<Artifact> requiredArtifacts;
  private List<Artifact> optionalArtifacts;
  private Boolean versioned;
  private Source source;
  private Artifact manifestArtifact;
  private String namespaceOverride;
  private boolean enableArtifactBinding = true;

  private boolean enableTraffic = true;
  private List<String> services;
  private Strategy strategy;
  private KubernetesSelectorList labelSelectors = new KubernetesSelectorList();

  /**
   * If false, and using (non-empty) label selectors, fail if a deploy manifest operation doesn't
   * deploy anything. If a particular deploy manifest stage intentionally specifies label selectors
   * that none of the resources satisfy, set this to true to allow the stage to succeed.
   */
  private boolean allowNothingSelected = false;

  public boolean isBlueGreen() {
    return Strategy.RED_BLACK.equals(this.strategy) || Strategy.BLUE_GREEN.equals(this.strategy);
  }

  public enum Source {
    artifact,
    text
  }

  public enum Strategy {
    RED_BLACK,
    BLUE_GREEN,
    HIGHLANDER,
    NONE
  }
}
