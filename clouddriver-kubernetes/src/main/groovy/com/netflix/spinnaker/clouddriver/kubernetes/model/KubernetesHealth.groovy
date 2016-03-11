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

package com.netflix.spinnaker.clouddriver.kubernetes.model

import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState
import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.Pod

class KubernetesHealth implements Health {
  HealthState state
  String source
  String type = "Kubernetes" // All healths are reported by Kubernetes

  KubernetesHealth(Pod pod) {
    source = "Pod"
    def phase = pod.status.phase
    def disabled = KubernetesUtil.getPodLoadBalancerStates(pod)?.every { _, v -> v == 'false' } && KubernetesUtil.getPodLoadBalancerStates(pod)?.size() != 0
    state = phase == "Pending" || disabled ? HealthState.OutOfService :
      phase == "Running" ? HealthState.Up :
        phase == "Succeeded" ? HealthState.Succeeded :
          phase == "Failed" ? HealthState.Failed : HealthState.Unknown
  }

  KubernetesHealth(String name, ContainerStatus containerStatus) {
    source = "Container $name"
    state = containerStatus.state.running && containerStatus.ready ? HealthState.Unknown :
      containerStatus.state.terminated ? HealthState.Down :
        containerStatus.state.waiting || !containerStatus.ready ? HealthState.OutOfService :
          HealthState.Unknown
  }
}
