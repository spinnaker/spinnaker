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
  String instanceId
  Long launchTime
  String zone
  List<Map<String, String>> health
  String serverGroupName
  Pod pod
  List<String> loadBalancers
  String providerType = "kubernetes"
  String yaml

  KubernetesInstance(Pod pod, List<String> loadBalancers) {
    this.name = pod.metadata?.name
    this.instanceId = this.name
    this.launchTime = KubernetesModelUtil.translateTime(pod.status?.startTime)
    this.zone = pod.metadata?.namespace
    this.pod = pod
    this.yaml = SerializationUtils.dumpWithoutRuntimeStateAsYaml(pod)
    this.loadBalancers = loadBalancers

    def mapper = new ObjectMapper()
    def podHealth = new KubernetesHealth(pod)

    this.health = pod.status?.containerStatuses?.collect {
      (Map<String, String>) mapper.convertValue(new KubernetesHealth(it.image, it, podHealth), new TypeReference<Map<String, String>>() {})
    } ?: []

    this.health << (Map<String, String>) mapper.convertValue(podHealth, new TypeReference<Map<String, String>>() {})

    this.serverGroupName = pod.metadata?.labels?.get(KubernetesUtil.REPLICATION_CONTROLLER_LABEL)
  }

  @Override
  HealthState getHealthState() {
    someUpRemainingUnknown(health) ? HealthState.Up :
        anyStarting(health) ? HealthState.Starting :
            anyDown(health) ? HealthState.Down :
                anyOutOfService(health) ? HealthState.OutOfService :
                    HealthState.Unknown
  }

  private static boolean anyDown(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.Down.name() }
  }

  private static boolean someUpRemainingUnknown(List<Map<String, String>> healthsList) {
    List<Map<String, String>> knownHealthList = healthsList.findAll { it.state != HealthState.Unknown.name() }
    knownHealthList ? knownHealthList.every { it.state == HealthState.Up.name() } : false
  }

  private static boolean anyStarting(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.Starting.name() }
  }

  private static boolean anyOutOfService(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.OutOfService.name() }
  }
}
