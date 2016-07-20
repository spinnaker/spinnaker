/*
 * Copyright 2016 Target Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity
import com.netflix.spinnaker.clouddriver.model.ServerGroup.ImageSummary
import com.netflix.spinnaker.clouddriver.model.ServerGroup.ImagesSummary
import com.netflix.spinnaker.clouddriver.model.ServerGroup.InstanceCounts
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import groovy.transform.Canonical
import groovy.transform.builder.Builder

@Builder
@Canonical
class OpenstackServerGroup implements ServerGroup, Serializable {
  String account
  String name
  String region
  Set<String> zones
  Set<OpenstackInstance> instances
  Set health
  Map<String, Object> image // Represented as map instead of OpenstackImage for convenience.
  Map<String, Object> launchConfig
  Map<String, Object> scalingConfig = [:]
  Long createdTime
  Set<String> loadBalancers
  Map<String, Object> buildInfo
  Boolean disabled
  String type = OpenstackCloudProvider.ID

  @JsonIgnore
  @Override
  Boolean isDisabled() { // Because groovy isn't smart enough to generate this method :-(
    disabled
  }

  @JsonIgnore
  @Override
  Set<String> getSecurityGroups() {
    (launchConfig && launchConfig.containsKey('securityGroups')) ? (Set<String>) launchConfig.securityGroups : []
  }

  @JsonIgnore
  @Override
  InstanceCounts getInstanceCounts() {
    new InstanceCounts(total: instances ? instances.size() : 0,
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0)
  }

  @JsonIgnore
  @Override
  Capacity getCapacity() {
    scalingConfig ?
      new Capacity(min: scalingConfig.minSize ? scalingConfig.minSize as Integer : 0, max: scalingConfig.maxSize ? scalingConfig.maxSize as Integer : 0)
      : null
  }

  @JsonIgnore
  @Override
  ImagesSummary getImagesSummary() {
    new DefaultImagesSummary(summaries: [new DefaultImageSummary(serverGroupName: name, imageName: image?.name, imageId: image?.id, buildInfo: buildInfo, image: image)])
  }

  @JsonIgnore
  @Override
  ImageSummary getImageSummary() {
    imagesSummary?.summaries?.getAt(0)
  }

  static Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

  static class DefaultImageSummary implements ImageSummary {
    String serverGroupName
    String imageId
    String imageName
    Map<String, Object> image
    Map<String, Object> buildInfo
  }

  static class DefaultImagesSummary implements ImagesSummary {
    List<ImageSummary> summaries
  }
}
