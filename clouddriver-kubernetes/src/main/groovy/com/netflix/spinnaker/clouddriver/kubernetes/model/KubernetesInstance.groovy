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
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.internal.SerializationUtils

class KubernetesInstance implements Instance, Serializable {
  String name
  String location
  String instanceId
  Long launchTime
  String zone
  List<Map<String, String>> health
  String controllerName
  Pod pod
  List<String> loadBalancers
  String providerType = "kubernetes"
  String yaml

  boolean isAttached(String serviceName) {
    KubernetesUtil.getPodLoadBalancerStates(pod)?.get(KubernetesUtil.loadBalancerKey(serviceName)) == "true"
  }

  KubernetesInstance(Pod pod, List<String> loadBalancers) {
    this.name = pod.metadata?.name
    this.location = pod.metadata?.namespace
    this.instanceId = this.name
    this.launchTime = KubernetesModelUtil.translateTime(pod.status?.startTime)
    this.zone = pod.metadata?.namespace
    this.pod = pod
    this.yaml = SerializationUtils.dumpWithoutRuntimeStateAsYaml(pod)
    this.loadBalancers = loadBalancers

    def mapper = new ObjectMapper()
    this.health = pod.status?.containerStatuses?.collect {
      (Map<String, String>) mapper.convertValue(new KubernetesHealth(it.image, it), new TypeReference<Map<String, String>>() {})
    } ?: []

    this.health.addAll(KubernetesUtil.getPodLoadBalancerStates(pod).collect { key, value ->
      (Map<String, String>) mapper.convertValue(new KubernetesHealth(key, value), new TypeReference<Map<String, String>>() {})
    } ?: [])

    this.health << (Map<String, String>) mapper.convertValue(new KubernetesHealth(pod), new TypeReference<Map<String, String>>() {})

    this.controllerName = pod.metadata?.labels?.get(KubernetesUtil.REPLICATION_CONTROLLER_LABEL) ?:
        pod.metadata?.labels?.get(KubernetesUtil.JOB_LABEL) ?: null
  }

  @Override
  HealthState getHealthState() {
    return KubernetesModelUtil.getHealthState(health)
  }
}
