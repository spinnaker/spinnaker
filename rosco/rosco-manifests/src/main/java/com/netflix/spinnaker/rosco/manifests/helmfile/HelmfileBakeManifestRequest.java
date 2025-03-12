/*
 * Copyright 2023 Grab Holdings, Inc.
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
 */

package com.netflix.spinnaker.rosco.manifests.helmfile;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HelmfileBakeManifestRequest extends BakeManifestRequest {
  private String helmfileFilePath;

  /**
   * The environment name used to customize the content of the helmfile manifest. The environment
   * name defaults to default.
   */
  private String environment;

  /** The namespace to be released into. */
  private String namespace;

  /**
   * The 0th element is (or contains) the helmfile template. The rest (possibly none) are values
   * files.
   */
  List<Artifact> inputArtifacts;

  /**
   * Include custom resource definition manifests in the templated output. Helmfile uses Helm v3
   * only which provides the option to include CRDs as part of the rendered output.
   */
  boolean includeCRDs;
}
