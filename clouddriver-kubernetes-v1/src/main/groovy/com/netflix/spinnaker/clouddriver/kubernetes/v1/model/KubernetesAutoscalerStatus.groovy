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

import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesModelUtil
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler
import groovy.util.logging.Slf4j

@Slf4j
class KubernetesAutoscalerStatus {
  Integer currentCpuUtilization
  Integer currentReplicas
  Integer desiredReplicas
  Long lastScaleTime

  KubernetesAutoscalerStatus() { }

  KubernetesAutoscalerStatus(HorizontalPodAutoscaler autoscaler) {
    if (autoscaler.status == null) {
      log.warn("Autoscaler on ${autoscaler.metadata.name} has a null status. The replicaset may be missing a CPU request.")
    } else {
      this.currentCpuUtilization = autoscaler.status.currentCPUUtilizationPercentage
      this.currentReplicas = autoscaler.status.currentReplicas
      this.desiredReplicas = autoscaler.status.desiredReplicas
      this.lastScaleTime = KubernetesModelUtil.translateTime(autoscaler.status.lastScaleTime)
    }
  }
}
