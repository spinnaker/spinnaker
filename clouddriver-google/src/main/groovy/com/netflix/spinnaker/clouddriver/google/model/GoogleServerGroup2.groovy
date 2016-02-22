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

package com.netflix.spinnaker.clouddriver.google.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.Canonical

@Canonical
class GoogleServerGroup2 {

  String name
  String region
  Set<String> zones = new HashSet<>()
  Set<GoogleInstance2> instances = new HashSet<>()
  Set health = new HashSet<>()
  Map<String, Object> launchConfig
  Map<String, Object> asg
  Set<String> securityGroups
  Map buildInfo
  Boolean disabled = false

  private Map<String, Object> dynamicProperties = new HashMap<String, Object>()

  @JsonAnyGetter
  public Map<String, Object> anyProperty() {
    return dynamicProperties;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    dynamicProperties.put(name, value);
  }

  @JsonIgnore
  View getView() {
    new View()
  }

  @Canonical
  class View implements ServerGroup {
    final String type = "gce"

    String name = GoogleServerGroup2.this.name
    String region = GoogleServerGroup2.this.region
    Set<String> zones = GoogleServerGroup2.this.zones
    Set<GoogleInstance2.View> instances = GoogleServerGroup2.this.instances.collect { it.view }
    Map<String, Object> launchConfig = GoogleServerGroup2.this.launchConfig
    Set<String> securityGroups = GoogleServerGroup2.this.securityGroups
    Boolean disabled = GoogleServerGroup2.this.disabled

    @Override
    Boolean isDisabled() { // Because groovy isn't smart enough to generate this method :-(
      disabled
    }

    @Override
    Long getCreatedTime() {
      launchConfig ? launchConfig.createdTime as Long : null
    }

    @Override
    ServerGroup.Capacity getCapacity() {
      def asg = GoogleServerGroup2.this.asg
      asg ?
          new ServerGroup.Capacity(min: asg.minSize ? asg.minSize as Integer : 0,
                                   max: asg.maxSize ? asg.maxSize as Integer : 0,
                                   desired: asg.desiredCapacity ? asg.desiredCapacity as Integer : 0) :
          null
    }

    @Override
    Set<String> getLoadBalancers() {
      Set<String> loadBalancerNames = []
      def asg = GoogleServerGroup2.this.asg
      if (asg && asg.containsKey("loadBalancerNames")) {
        loadBalancerNames = (Set<String>) asg.loadBalancerNames
      }
      return loadBalancerNames
    }

    @Override
    ServerGroup.ImageSummary getImageSummary() {
      def bi = GoogleServerGroup2.this.buildInfo
      return new ServerGroup.ImageSummary() {
        String serverGroupName = name
        String imageName = launchConfig?.instanceTemplate?.name
        String imageId = launchConfig?.imageId

        @Override
        Map<String, Object> getBuildInfo() {
          return bi
        }

        @Override
        Map<String, Object> getImage() {
          return launchConfig?.instanceTemplate
        }
      }
    }

    @Override
    ServerGroup.InstanceCounts getInstanceCounts() {
      new ServerGroup.InstanceCounts(
          total: instances.size(),
          up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
          down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
          unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
          starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
          outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0
      )
    }

    static Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
      instances.findAll { Instance it -> it.getHealthState() == healthState }
    }
  }
}
