/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.model
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.EqualsAndHashCode
import org.cloudfoundry.client.lib.domain.CloudApplication
/**
 * A Cloud Foundry application combined with its org/space coordinates.
 */
@EqualsAndHashCode(includes = ["name"])
class CloudFoundryServerGroup implements ServerGroup, Serializable {

  String name
  String type = 'cf'
  CloudApplication nativeApplication
  Boolean disabled = true
  Set<CloudFoundryApplicationInstance> instances = new HashSet<>()
  int memory
  int disk
  Set<CloudFoundryService> services = [] as Set<CloudFoundryService>
  Map<String, Object> cfSettings = new HashMap<>() // scaling, memory, etc.
  Set<CloudFoundryLoadBalancer> nativeLoadBalancers
  Map buildInfo
  String consoleLink
  String logsLink

  @Override
  String getRegion() {
    nativeApplication.space.organization.name
  }

  @Override
  Boolean isDisabled() {
    disabled
  }

  /**
   * Return an instance's "since" attribute if it exists, fetching the oldest one available. Otherwise, fallback to "updated"
   * @return
   */
  @Override
  Long getCreatedTime() {
    if (this.instances.size() > 0) {
      this.instances.collect { it.launchTime }.min()
    } else {
      nativeApplication?.meta?.updated?.time
    }
  }

  @Override
  Set<String> getZones() {
    Collections.singleton(nativeApplication.space.name)
  }

  @Override
  Set<String> getLoadBalancers() {
    if (nativeLoadBalancers != null) {
      nativeLoadBalancers.collect { it?.name } as Set<String>
    } else {
      [] as Set
    }
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    nativeApplication.envAsMap
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<CloudFoundryApplicationInstance> instances = getInstances()
    new ServerGroup.InstanceCounts(
        total: instances.size(),
        up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
        down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
        unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
        starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
        outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0
    )
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    new ServerGroup.Capacity([
        min: nativeApplication.instances,
        max: nativeApplication.instances,
        desired: nativeApplication.instances
    ])
  }

  Map<String, Object> getAsg() {
    if (nativeApplication) {
      return [
          minSize: nativeApplication.instances,
          maxSize: nativeApplication.instances,
          desiredCapacity: nativeApplication.instances
      ]
    }
    return null
  }

  @Override
  Set<String> getSecurityGroups() {
    Collections.emptySet()
  }

  @Override
  ServerGroup.ImagesSummary getImagesSummary() {
    return new ServerGroup.ImagesSummary() {
      @Override
      List<ServerGroup.ImageSummary> getSummaries() {
        def bi = buildInfo
        return [new ServerGroup.ImageSummary() {
          String serverGroupName = name
          String imageName = launchConfig?."${CloudFoundryConstants.ARTIFACT}"
          String imageId = launchConfig?."${CloudFoundryConstants.REPOSITORY}" +
            "::" + launchConfig?."${CloudFoundryConstants.ARTIFACT}" +
            "::" + launchConfig?."${CloudFoundryConstants.ACCOUNT}"

          @Override
          Map<String, Object> getBuildInfo() {
            bi
          }

          @Override
          Map<String, Object> getImage() {
            return [
              imageName: imageName,
              imageId  : imageId
            ]
          }
        }]
      }
    }
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    imagesSummary?.summaries?.get(0)
  }

  static Collection<Instance> filterInstancesByHealthState(Set<CloudFoundryApplicationInstance> instances,
                                                           HealthState healthState) {
    instances.findAll { CloudFoundryApplicationInstance it -> it.getHealthState() == healthState }
  }

}
