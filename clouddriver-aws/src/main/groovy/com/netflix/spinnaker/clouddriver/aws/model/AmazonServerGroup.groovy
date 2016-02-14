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

package com.netflix.spinnaker.clouddriver.aws.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.CompileStatic

@CompileStatic
class AmazonServerGroup implements ServerGroup, Serializable {

  String name
  String region
  Set<String> zones
  Set<Instance> instances
  Set health
  Map<String, Object> image
  Map<String, Object> launchConfig
  Map<String, Object> asg
  List<Map> scalingPolicies
  List<Map> scheduledActions
  Map buildInfo
  String vpcId

  private Map<String, Object> dynamicProperties = new HashMap<String, Object>()

  @JsonAnyGetter
  public Map<String,Object> any() {
    return dynamicProperties;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    dynamicProperties.put(name, value);
  }

  @Override
  String getType() {
    return "aws"
  }

  @Override
  Boolean isDisabled() {
    if (asg) {
      List<Map> suspendedProcesses = (List<Map>) asg.suspendedProcesses
      List<String> processNames = (List<String>) suspendedProcesses.collect { it.processName }
      return processNames.contains('AddToLoadBalancer')
    }
    return false
  }

  @Override
  Long getCreatedTime() {
    if (!asg) {
      return null
    }
    (Long) asg.createdTime
  }

  @Override
  Set<String> getLoadBalancers() {
    Set<String> loadBalancerNames = []
    def asg = getAsg()
    if (asg && asg.containsKey("loadBalancerNames")) {
      loadBalancerNames = (Set<String>) asg.loadBalancerNames
    }
    return loadBalancerNames
  }

  @Override
  Set<String> getSecurityGroups() {
    Set<String> securityGroups = []
    if (launchConfig && launchConfig.containsKey("securityGroups")) {
      securityGroups = (Set<String>) launchConfig.securityGroups
    }
    securityGroups
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Collection<Instance> instances = getInstances()
    new ServerGroup.InstanceCounts(
      total: instances.size(),
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0)
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    if (asg) {
      return new ServerGroup.Capacity(
        min: asg.minSize ? asg.minSize as Integer : 0,
        max: asg.maxSize ? asg.maxSize as Integer : 0,
        desired: asg.desiredCapacity ? asg.desiredCapacity as Integer : 0
      )
    }
    return null
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    def i = image
    def bi = buildInfo
    return new ServerGroup.ImageSummary() {
      String serverGroupName = name
      String imageName = i?.name
      String imageId = i?.imageId

      @Override
      Map<String, Object> getBuildInfo() {
        return bi
      }

      @Override
      Map<String, Object> getImage() {
        return i
      }
    }
  }

  static Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

}
