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

package com.netflix.spinnaker.clouddriver.titus.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState

/**
 * Equivalent of a Titus {@link com.netflix.spinnaker.clouddriver.titus.client.model.Job}
 *
 */
class TitusServerGroup implements ServerGroup, Serializable {

  public static final String TYPE = Keys.PROVIDER

  String id
  String name
  String type = TYPE
  Map env
  Long submittedAt
  String application
  Map<String, Object> image = [:]
  Set<Instance> instances = [] as Set
  ServerGroup.Capacity capacity
  TitusServerGroupResources resources = new TitusServerGroupResources()
  TitusServerGroupPlacement placement = new TitusServerGroupPlacement()
  boolean disabled

  TitusServerGroup() {}

  TitusServerGroup(Job job) {
    id = job.id
    name = job.name
    image << [dockerImageName: job.applicationName]
    image << [dockerImageVersion: job.version]
    resources.cpu = job.cpu
    resources.memory = job.memory
    resources.disk = job.disk
    resources.ports = job.ports ? job.ports.toList() : []
    env = job.environment
    submittedAt = job.submittedAt ? job.submittedAt.time : null
    application = Names.parseName(job.name).app
    placement.account = job.environment.account
    placement.region = job.tasks.findResult { it.region }
    instances = job.tasks.findAll { it != null }.collect { new TitusInstance(job, it) } as Set
    capacity = new ServerGroup.Capacity(min: job.instances, max: job.instances, desired: job.instances)
    //TODO(cfieber) - more of the 'disable is stop all the tasks' nonsense here:
    disabled = job.tasks.every { it.state == TaskState.STOPPED }
  }

  @Override
  String getRegion() {
    placement.region
  }

  @Override
  Boolean isDisabled() {
    disabled
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
