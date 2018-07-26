/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.model

import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import io.fabric8.kubernetes.api.model.apps.Deployment


class KubernetesDeploymentStatus {
  Integer replicas
  Integer availableReplicas
  Integer unavailableReplicas
  Integer updatedReplicas
  String revision

  KubernetesDeploymentStatus(Deployment deployment) {
    replicas = deployment.status.replicas
    availableReplicas = deployment.status.availableReplicas
    unavailableReplicas = deployment.status.unavailableReplicas
    updatedReplicas = deployment.status.updatedReplicas
    revision = KubernetesApiAdaptor.getDeploymentRevision(deployment)
  }
}
