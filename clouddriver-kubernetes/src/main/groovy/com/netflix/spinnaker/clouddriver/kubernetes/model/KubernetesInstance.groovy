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
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import io.fabric8.kubernetes.api.model.ContainerState
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.internal.SerializationUtils

class KubernetesInstance implements Instance, Serializable {
  String name
  HealthState healthState
  Long launchTime
  String zone
  List<Map<String, String>> health
  String serverGroupName
  Pod pod
  String yaml

  static HealthState convertContainerState(ContainerState state) {
    state?.running ? HealthState.Up :
    state?.waiting ? HealthState.Starting :
    state?.terminated ? HealthState.Down :
    HealthState.Unknown
  }

  KubernetesInstance(Pod pod) {
    this.name = pod.metadata?.name
    this.launchTime = KubernetesModelUtil.translateTime(pod.status?.startTime)
    this.zone = pod.metadata?.namespace
    this.health = []
    this.pod = pod
    this.yaml = SerializationUtils.dumpWithoutRuntimeStateAsYaml(pod)
    pod.status?.containerStatuses?.forEach {
      this.health << [name: it.name,
                      state: convertContainerState(it.state).toString(),
                      image: it.image,
                      ready: it.ready.toString(),
                      running: it.state?.running?.toString(),
                      waiting: it.state?.waiting?.toString(),
                      terminated: it.state?.terminated?.toString()]
    }

    def phase = pod.status?.phase
    if (!phase) {
      healthState = HealthState.Unknown
    } else {
      healthState = phase == "Running" ? HealthState.Up :
        phase == "Pending" ? HealthState.Starting :
        phase == "Succeeded" ? HealthState.Down : // TODO(lwander): this needs a special designation
        phase == "Failed" ? HealthState.Down: HealthState.Unknown
    }

    this.serverGroupName = pod.metadata?.labels?.get(KubernetesUtil.REPLICATION_CONTROLLER_LABEL)
  }
}
