/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.ServerGroup

class GoogleServerGroup implements ServerGroup, Serializable {

  private static final String GOOGLE_SERVER_GROUP_TYPE = "gce"

  String name
  String region
  Set<String> zones = new HashSet<>()
  Set<Instance> instances = new HashSet<>()
  Set health = new HashSet<>()
  Map<String, Object> launchConfig
  Map<String, Object> asg
  Map buildInfo
  Boolean disabled = true

  private Map<String, Object> dynamicProperties = new HashMap<String, Object>()

  // Used as a deep copy-constructor.
  public static GoogleServerGroup newInstance(GoogleServerGroup originalGoogleServerGroup) {
    GoogleServerGroup copyGoogleServerGroup = new GoogleServerGroup()

    copyGoogleServerGroup.setDisabled(originalGoogleServerGroup.isDisabled())

    originalGoogleServerGroup.getMetaClass().getProperties().each { metaProperty ->
      def propertyName = metaProperty.name

      if (propertyName.equals("instances")) {
        originalGoogleServerGroup.instances.each { originalInstance ->
          copyGoogleServerGroup.instances << GoogleInstance.newInstance((GoogleInstance) originalInstance)
        }
      } else {
        def valueCopy = Utils.getImmutableCopy(originalGoogleServerGroup.getProperty(propertyName))

        if (valueCopy) {
          copyGoogleServerGroup.setProperty(propertyName, valueCopy)
        }
      }
    }

    copyGoogleServerGroup
  }

  @JsonAnyGetter
  public Map<String, Object> anyProperty() {
    return dynamicProperties;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    dynamicProperties.put(name, value);
  }

  @Override
  String getType() {
    return GOOGLE_SERVER_GROUP_TYPE
  }

  @Override
  Boolean isDisabled() {
    return disabled
  }

  @Override
  Long getCreatedTime() {
    if (launchConfig) {
      return launchConfig.createdTime
    }
    return null
  }

  @Override
  Set<String> getLoadBalancers() {
    Set<String> loadBalancerNames = []
    if (asg && asg.containsKey("loadBalancerNames")) {
      loadBalancerNames = (Set<String>) asg.loadBalancerNames
    }
    return loadBalancerNames
  }

  @Override
  Set<String> getSecurityGroups() {
    return null
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
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0
    )
  }

  static Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

}
