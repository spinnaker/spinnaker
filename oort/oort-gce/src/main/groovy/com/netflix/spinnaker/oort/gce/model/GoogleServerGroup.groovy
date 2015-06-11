/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.ServerGroup

class GoogleServerGroup extends HashMap implements ServerGroup, Serializable {

  private boolean disabled = true

  GoogleServerGroup() {
    this(null, null, null)
  }

  GoogleServerGroup(String name, String type, String region) {
    setProperty "name", name
    setProperty "type", type
    setProperty "region", region
    setProperty "zones", new HashSet<>()
    setProperty "instances", new HashSet<>()
    setProperty "health", new HashSet<>()
  }

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

  @Override
  String getName() {
    getProperty "name"
  }

  @Override
  String getType() {
    getProperty "type"
  }

  @Override
  String getRegion() {
    getProperty "region"
  }

  @Override
  Set<String> getSecurityGroups() {
    (Set<String>) getProperty("securityGroups")
  }

  @Override
  Set<String> getLoadBalancers() {
    def loadBalancerNames = []
    def asg = getAsg()
    if (asg && asg.containsKey("loadBalancerNames")) {
      loadBalancerNames = asg.loadBalancerNames
    }
    return loadBalancerNames
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    (Map<String, Object>) getProperty("launchConfig")
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<Instance> instances = getInstances()
    def total = instances.size()
    def up = filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0
    def down = filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0
    def unknown = filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0
    new ServerGroup.InstanceCounts(total: total, up: up, down: down, unknown: unknown)
  }

  static Set filterInstancesByHealthState(Set instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

  @Override
  Boolean isDisabled() {
    return disabled
  }

  void setDisabled(boolean disabled) {
    this.disabled = disabled
  }

  @Override
  Long getCreatedTime() {
    def launchConfig = getLaunchConfig()
    if (launchConfig) {
      return launchConfig.createdTime
    }
    return null
  }

  @Override
  Set<String> getZones() {
    (Set<String>) getProperty("zones")
  }

  @Override
  Set<Instance> getInstances() {
    (Set<Instance>) getProperty("instances")
  }

  Map getBuildInfo() {
    (Map) getProperty("buildInfo")
  }

  private Map<String, Object> getAsg() {
    (Map<String, Object>) getProperty("asg")
  }

}
