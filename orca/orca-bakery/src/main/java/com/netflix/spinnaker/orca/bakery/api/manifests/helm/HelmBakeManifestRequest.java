/*
 * Copyright 2018 Mirantis, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.bakery.api.manifests.helm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.bakery.api.manifests.BakeManifestRequest;
import com.netflix.spinnaker.orca.bakery.tasks.manifests.BakeManifestContext;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HelmBakeManifestRequest extends BakeManifestRequest {

  @JsonProperty("apiVersions")
  private String apiVersions;

  @JsonProperty("kubeVersion")
  private String kubeVersion;

  @JsonProperty("namespace")
  private String namespace;

  @JsonProperty("overrides")
  private Map<String, Object> overrides;

  @JsonProperty("inputArtifacts")
  private List<Artifact> inputArtifacts;

  private List<Artifact> values;

  @JsonProperty("rawOverrides")
  private Boolean rawOverrides;

  @JsonProperty("includeCRDs")
  private Boolean includeCRDs;

  @JsonProperty("helmChartFilePath")
  private String helmChartFilePath;

  public HelmBakeManifestRequest(
      BakeManifestContext bakeManifestContext,
      List<Artifact> inputArtifacts,
      String outputArtifactName,
      Map<String, Object> overrides) {
    super(
        bakeManifestContext.getTemplateRenderer(),
        outputArtifactName,
        bakeManifestContext.getOutputName());
    this.setOverrides(overrides);
    this.setApiVersions(bakeManifestContext.getApiVersions());
    this.setKubeVersion(bakeManifestContext.getKubeVersion());
    this.setNamespace(bakeManifestContext.getNamespace());
    this.setInputArtifacts(inputArtifacts);
    this.setRawOverrides(bakeManifestContext.getRawOverrides());
    this.setIncludeCRDs(bakeManifestContext.getIncludeCRDs());
    this.setHelmChartFilePath(bakeManifestContext.getHelmChartFilePath());
  }
}
