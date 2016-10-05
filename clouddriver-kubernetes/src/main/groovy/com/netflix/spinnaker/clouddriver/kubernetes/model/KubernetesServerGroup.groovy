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

import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.EqualsAndHashCode
import io.fabric8.kubernetes.api.model.Event
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.extensions.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet
import io.fabric8.kubernetes.client.internal.SerializationUtils

@EqualsAndHashCode(includes = ["name"])
class KubernetesServerGroup implements ServerGroup, Serializable {
  String name
  String type = "kubernetes"
  String region
  String namespace
  String account
  Long createdTime
  Integer replicas = 0
  Set<String> zones
  Set<KubernetesInstance> instances
  Set<String> loadBalancers = [] as Set
  Set<String> securityGroups = [] as Set
  Map<String, Object> launchConfig
  Map<String, String> labels = [:]
  DeployKubernetesAtomicOperationDescription deployDescription
  KubernetesAutoscalerStatus autoscalerStatus
  String kind // Kubernetes resource-type
  String yaml
  Map buildInfo
  List<KubernetesEvent> events

  Boolean isDisabled() {
    this.labels ? !(this.labels.any { key, value -> KubernetesUtil.isLoadBalancerLabel(key) && value == "true" }) : false
  }

  KubernetesServerGroup() { }

  KubernetesServerGroup(String name, String namespace) {
    this.name = name
    this.region = namespace
    this.namespace = namespace
  }

  KubernetesServerGroup(ReplicaSet replicaSet, String account, List<Event> events, HorizontalPodAutoscaler autoscaler) {
    this.name = replicaSet.metadata?.name
    this.account = account
    this.region = replicaSet.metadata?.namespace
    this.namespace = this.region
    this.createdTime = KubernetesModelUtil.translateTime(replicaSet.metadata?.creationTimestamp)
    this.zones = [this.region] as Set
    this.securityGroups = []
    this.replicas = replicaSet.spec?.replicas ?: 0
    this.loadBalancers = KubernetesUtil.getLoadBalancers(replicaSet) as Set
    this.launchConfig = [:]
    this.labels = replicaSet.spec?.template?.metadata?.labels
    this.deployDescription = KubernetesApiConverter.fromReplicaSet(replicaSet)
    this.yaml = SerializationUtils.dumpWithoutRuntimeStateAsYaml(replicaSet)
    this.kind = replicaSet.kind
    this.events = events.collect {
      new KubernetesEvent(it)
    }
    if (autoscaler) {
      KubernetesApiConverter.attachAutoscaler(this.deployDescription, autoscaler)
      this.autoscalerStatus = new KubernetesAutoscalerStatus(autoscaler)
    }
  }

  KubernetesServerGroup(ReplicationController replicationController, String account, List<Event> events, HorizontalPodAutoscaler autoscaler) {
    this.name = replicationController.metadata?.name
    this.account = account
    this.region = replicationController.metadata?.namespace
    this.namespace = this.region
    this.createdTime = KubernetesModelUtil.translateTime(replicationController.metadata?.creationTimestamp)
    this.zones = [this.region] as Set
    this.securityGroups = []
    this.replicas = replicationController.spec?.replicas ?: 0
    this.loadBalancers = KubernetesUtil.getLoadBalancers(replicationController) as Set
    this.launchConfig = [:]
    this.labels = replicationController.spec?.template?.metadata?.labels
    this.deployDescription = KubernetesApiConverter.fromReplicationController(replicationController)
    this.yaml = SerializationUtils.dumpWithoutRuntimeStateAsYaml(replicationController)
    this.kind = replicationController.kind
    this.events = events.collect {
      new KubernetesEvent(it)
    }
    if (autoscaler) {
      KubernetesApiConverter.attachAutoscaler(this.deployDescription, autoscaler)
      this.autoscalerStatus = new KubernetesAutoscalerStatus(autoscaler)
    }
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    new ServerGroup.InstanceCounts(
      down: (Integer) instances?.count { it.healthState == HealthState.Down } ?: 0,
      outOfService: (Integer) instances?.count { it.healthState == HealthState.OutOfService } ?: 0,
      up: (Integer) instances?.count { it.healthState == HealthState.Up } ?: 0,
      starting: (Integer) instances?.count { it.healthState == HealthState.Starting } ?: 0,
      unknown: (Integer) instances?.count { it.healthState == HealthState.Unknown } ?: 0,
      total: (Integer) instances?.size(),
    )
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    new ServerGroup.Capacity(min: replicas, max: replicas, desired: replicas)
  }

  @Override
  ServerGroup.ImagesSummary getImagesSummary() {
    def bi = buildInfo
    return new ServerGroup.ImagesSummary() {
      @Override
      List<? extends ServerGroup.ImageSummary> getSummaries () {
        deployDescription.containers.collect({ KubernetesContainerDescription it ->
          new ServerGroup.ImageSummary() {
            String serverGroupName = name
            String imageName = it.name
            String imageId = KubernetesUtil.getImageId(it.imageDescription)

            @Override
            Map<String, Object> getBuildInfo() {
              return bi
            }

            @Override
            Map<String, Object> getImage() {
              return (Map<String, Object>) [
                container: it.name,
                registry: it.imageDescription.registry,
                tag: it.imageDescription.tag,
                repository: it.imageDescription.repository,
                imageId: imageId
              ]
            }
          }
        })
      }
    }
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    imagesSummary?.summaries?.get(0)
  }
}
