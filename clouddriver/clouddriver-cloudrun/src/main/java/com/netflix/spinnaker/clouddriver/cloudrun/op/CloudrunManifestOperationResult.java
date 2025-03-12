/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.op;

import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunService;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CloudrunManifestOperationResult extends DeploymentResult {

  private Set<CloudrunService> manifests = new HashSet<>();

  public CloudrunManifestOperationResult addManifest(CloudrunService manifest) {
    manifests.add(manifest);
    return this;
  }
}
