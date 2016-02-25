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
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ReplicationController

@CompileStatic
@EqualsAndHashCode(includes = ["name"])
class KubernetesServerGroup implements ServerGroup, Serializable {
  String name
  String type = "kubernetes"
  String region
  String account
  Long createdTime
  Integer replicas = 0
  Set<String> zones
  Set<KubernetesInstance> instances
  Set<String> loadBalancers
  Set<String> securityGroups
  Map<String, Object> launchConfig
  Map<String, String> labels = [:]
  List<Container> containers
  ReplicationController replicationController

  Boolean isDisabled() {
    this.labels ? !(this.labels.any { key, value -> KubernetesUtil.isLoadBalancerLabel(key) && value == "true" }) : false
  }

  KubernetesServerGroup(String name, String namespace) {
    this.name = name
    this.region = namespace
  }

  KubernetesServerGroup(ReplicationController replicationController, Set<KubernetesInstance> instances, String account) {
    this.name = replicationController.metadata?.name
    this.account = account
    this.region = replicationController.metadata?.namespace
    this.createdTime = KubernetesModelUtil.translateTime(replicationController.metadata?.creationTimestamp)
    this.zones = [this.region] as Set
    this.instances = instances
    this.securityGroups = []
    this.replicas = replicationController.spec?.replicas ?: 0
    this.loadBalancers = KubernetesUtil.getDescriptionLoadBalancers(replicationController) as Set
    this.launchConfig = [:]
    this.labels = replicationController.spec?.template?.metadata?.labels
    this.containers = replicationController.spec?.template?.spec?.containers
    this.replicationController = replicationController
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    new ServerGroup.InstanceCounts(
      down: (Integer) instances?.count { it.healthState == HealthState.Down } ?: 0,
      outOfService: (Integer) instances?.count { it.healthState == HealthState.OutOfService } ?: 0,
      up: (Integer) instances?.count { it.healthState == HealthState.Up } ?: 0,
      starting: (Integer) instances?.count { it.healthState == HealthState.Starting } ?: 0,
      unknown: (Integer) instances?.count { it.healthState == HealthState.Unknown } ?: 0
    )
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    new ServerGroup.Capacity(min: replicas, max: replicas, desired: replicas)
  }

  @Override
  ServerGroup.ImagesSummary getImagesSummary() {
    return new ServerGroup.ImagesSummary() {
      @Override
      List<ServerGroup.ImageSummary> getSummaries () {
        containers.collect({ Container it ->
          new ServerGroup.ImageSummary() {
            String serverGroupName = name
            String imageName = it.name
            String imageId = it.image

            @Override
            Map<String, Object> getBuildInfo() {
              return it.additionalProperties
            }

            @Override
            Map<String, Object> getImage() {
              return [image: it.image, name: it.name]
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
