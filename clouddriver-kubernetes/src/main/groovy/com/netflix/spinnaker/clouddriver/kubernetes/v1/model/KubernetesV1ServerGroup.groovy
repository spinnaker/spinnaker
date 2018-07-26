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

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesModelUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesClientApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.fabric8.kubernetes.api.model.Event
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.client.internal.SerializationUtils
import io.kubernetes.client.models.V1beta1DaemonSet
import io.kubernetes.client.models.V1beta1StatefulSet

import static com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil.ENABLE_DISABLE_ANNOTATION

@CompileStatic
@EqualsAndHashCode(includes = ["name", "namespace", "account"])
class KubernetesV1ServerGroup implements ServerGroup, Serializable {
  String name
  final String type = KubernetesCloudProvider.ID
  final String cloudProvider = KubernetesCloudProvider.ID
  String region
  String namespace
  String account
  Long createdTime
  Integer replicas = 0
  Boolean hostNetwork = false
  Set<String> zones
  Set<KubernetesV1Instance> instances
  Set<String> loadBalancers = [] as Set
  Set<String> securityGroups = [] as Set
  Map<String, Object> launchConfig
  Map<String, String> labels = [:]
  Map<String, String> annotations = [:]
  DeployKubernetesAtomicOperationDescription deployDescription
  KubernetesAutoscalerStatus autoscalerStatus
  KubernetesDeploymentStatus deploymentStatus
  String kind // Kubernetes resource-type
  String yaml
  String revision
  Map buildInfo
  List<KubernetesEvent> events

  Map<String, Object> getBuildInfo() {
    def imageList = []
    def buildInfo = [:]
    /**
     * I have added a null check as in statefullset deployDescription is null
     */
    if (deployDescription != null) {
      for (def container : this.deployDescription.containers) {
        imageList.add(KubernetesUtil.getImageIdWithoutRegistry(container.imageDescription))
      }

      buildInfo.images = imageList

      def parsedName = Names.parseName(name)

      buildInfo.createdBy = this.deployDescription?.deployment?.enabled ? parsedName.cluster : null
    }
    return buildInfo
  }

  Boolean isDisabled() {
    if (replicas == 0) {
      return true
    }

    if (labels) {
      def lbCount = labels.count { key, value -> KubernetesUtil.isLoadBalancerLabel(key) }
      if (lbCount == 0) {
        return annotations?.get(ENABLE_DISABLE_ANNOTATION) == "false"
      }

      def enabledCount = labels.count { key, value -> KubernetesUtil.isLoadBalancerLabel(key) && value == "true" }
      return enabledCount == 0
    }

    return false
  }

  KubernetesV1ServerGroup() { }

  KubernetesV1ServerGroup(String name, String namespace) {
    this.name = name
    this.region = namespace
    this.namespace = namespace
  }

  KubernetesV1ServerGroup(V1beta1StatefulSet statefulSet, String account, List<Event> events) {
    this.name = statefulSet.metadata?.name
    this.account = account
    this.region = statefulSet.metadata?.namespace
    this.namespace = this.region
    this.createdTime = statefulSet.metadata?.creationTimestamp?.getMillis()
    this.zones = [this.region] as Set
    this.securityGroups = []
    this.replicas = statefulSet.spec?.replicas ?: 0
    this.launchConfig = [:]
    this.labels = statefulSet.spec?.template?.metadata?.labels
    this.deployDescription = KubernetesClientApiConverter.fromStatefulSet(statefulSet)
    this.yaml = KubernetesClientApiConverter.getYaml(statefulSet)
    this.kind = statefulSet.kind
    this.events = events?.collect {
      new KubernetesEvent(it)
    }
  }

  KubernetesV1ServerGroup(V1beta1DaemonSet daemonSet, String account, List<Event> events) {
    this.name = daemonSet.metadata?.name
    this.account = account
    this.region = daemonSet.metadata?.namespace
    this.namespace = this.region
    this.createdTime = daemonSet.metadata?.creationTimestamp?.getMillis()
    this.zones = [this.region] as Set
    this.securityGroups = []
    this.launchConfig = [:]
    this.labels = daemonSet.spec?.template?.metadata?.labels
    this.deployDescription = KubernetesClientApiConverter.fromDaemonSet(daemonSet)
    this.yaml = KubernetesClientApiConverter.getYaml(daemonSet)
    this.kind = daemonSet.kind
    this.events = events?.collect {
      new KubernetesEvent(it)
    }
  }

  KubernetesV1ServerGroup(ReplicaSet replicaSet, String account, List<Event> events, HorizontalPodAutoscaler autoscaler) {
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
    this.annotations = replicaSet.metadata?.annotations
    this.events = events?.collect {
      new KubernetesEvent(it)
    }
    if (autoscaler) {
      KubernetesApiConverter.attachAutoscaler(this.deployDescription, autoscaler)
      this.autoscalerStatus = new KubernetesAutoscalerStatus(autoscaler)
    }
    this.revision = KubernetesApiAdaptor.getDeploymentRevision(replicaSet)
  }

  KubernetesV1ServerGroup(ReplicationController replicationController, String account, List<Event> events, HorizontalPodAutoscaler autoscaler) {
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
    this.annotations = replicationController.metadata?.annotations
    this.events = events?.collect {
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
