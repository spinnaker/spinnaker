/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.manifest;

import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesEnableDisableManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.CanLoadBalance;
import java.util.List;

public class KubernetesDisableManifestOperation
    extends AbstractKubernetesEnableDisableManifestOperation {
  public KubernetesDisableManifestOperation(
      KubernetesEnableDisableManifestDescription description) {
    super(description);
  }

  @Override
  protected String getVerbName() {
    return "disable";
  }

  @Override
  protected List<JsonPatch> patchResource(
      CanLoadBalance loadBalancerHandler,
      KubernetesManifest loadBalancer,
      KubernetesManifest target) {
    return loadBalancerHandler.detachPatch(loadBalancer, target);
  }
}
