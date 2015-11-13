/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.oort.titan.model
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.titanclient.model.Job

/**
 * Equivalent of a Titan {@link com.netflix.titanclient.model.Job}
 *
 */
class TitanServerGroup implements ServerGroup, Serializable {

  public static final String TYPE = "titan"

  String id
  String name
  String type = TYPE
  Map env
  Long submittedAt
  String application
  Map<String, Object> image = [:]
  Set<Instance> instances = [] as Set
  ServerGroup.Capacity capacity
  TitanServerGroupResources resources = new TitanServerGroupResources()
  TitanServerGroupPlacement placement = new TitanServerGroupPlacement()

  TitanServerGroup() {}

  TitanServerGroup(Job job) {
    id = job.id
    name = job.name
    image << [dockerImageName: job.dockerImageName]
    image << [dockerImageVersion: job.dockerImageVersion]
    resources.cpu = job.cpu
    resources.memory = job.memory
    resources.disk = job.disk
    resources.ports = job.ports ? Arrays.asList(job.ports) : []
    env = job.env
    submittedAt = job.submittedAt ? job.submittedAt.time : null
    application = job.application
    placement.account = job.account
    placement.region = job.region
    placement.subnetId = job.subnetId
    instances = job.tasks.findAll { it != null }.collect { new TitanInstance(it) } as Set
    capacity = new ServerGroup.Capacity(min: job.instances, max: job.instances, desired: job.instances)
  }

  @Override
  String getRegion() {
    placement.region
  }

  @Override
  Boolean isDisabled() {
    false
  }

  @Override
  Long getCreatedTime() {
    submittedAt
  }

  @Override
  Set<String> getLoadBalancers() {
    [] as Set
  }

  @Override
  Set<String> getSecurityGroups() {
    [] as Set
  }

  @Override
  Set<String> getZones() {
    placement.zones as Set
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    [:]
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<Instance> instances = getInstances()
    new ServerGroup.InstanceCounts(
      total: instances.size(),
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0)
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    def i = image
    return new ServerGroup.ImageSummary() {
      String serverGroupName = name
      // TODO(sthadeshwar): Give these values
      String imageName
      String imageId

      @Override
      Map<String, Object> getBuildInfo() {
        // TODO(sthadeshwar): Where to get build info?
        return null
      }

      @Override
      Map<String, Object> getImage() {
        return i
      }
    }
  }

  static Set filterInstancesByHealthState(Set instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

}
