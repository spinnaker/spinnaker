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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Process
import io.fabric8.kubernetes.api.model.Pod

class KubernetesProcess implements Process, Serializable {
  String name
  String id
  String location
  String jobId
  List<Map<String, String>> health
  List<String> loadBalancers
  Pod pod

  KubernetesProcess(Pod pod, List<String> loadBalancers) {
    this.name = pod.metadata.name
    this.loadBalancers = loadBalancers
    this.id = this.name
    this.location = pod.metadata.namespace

    def mapper = new ObjectMapper()
    this.health = pod.status?.containerStatuses?.collect {
      (Map<String, String>) mapper.convertValue(new KubernetesHealth(it.image, it), new TypeReference<Map<String, String>>() {})
    } ?: []
    this.health << (Map<String, String>) mapper.convertValue(new KubernetesHealth(pod), new TypeReference<Map<String, String>>() {})

    this.pod = pod
  }

  @Override
  HealthState getHealthState() {
    return KubernetesModelUtil.getHealthState(health)
  }
}
