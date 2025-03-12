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

import com.amazonaws.services.ec2.model.RequestLaunchTemplateData
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.documentation.Empty
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.kork.annotations.Alpha
import groovy.transform.CompileStatic

@CompileStatic
class AmazonServerGroup implements ServerGroup, Serializable {

  String name
  String region
  Set<String> zones
  Set<Instance> instances
  Set health
  Map<String, Object> image

  /**
   * An ASG can be configured with a launchConfig or launchTemplate or mixedInstancesPolicy.
   */
  Map<String, Object> launchConfig
  Map<String, Object> launchTemplate
  /**
   * The fields marked @Alpha are subject to change in the future due to addition of newer ASG features like multiple launch templates.
   */
  @Alpha
  MixedInstancesPolicySettings mixedInstancesPolicy

  Map<String, Object> asg
  List<Map> scalingPolicies
  List<Map> scheduledActions
  Map buildInfo
  String vpcId
  final String type = AmazonCloudProvider.ID
  final String cloudProvider = AmazonCloudProvider.ID

  Set<String> targetGroups

  @JsonIgnore
  private Map<String, Object> extraAttributes = new LinkedHashMap<String, Object>()

  @Override
  @JsonAnyGetter
  Map<String,Object> getExtraAttributes() {
    return extraAttributes
  }

  /**
   * Setter for non explicitly defined values.
   *
   * Used for both Jackson mapping {@code @JsonAnySetter} as well
   * as Groovy's implicit Map constructor (this is the reason the
   * method is named {@code set(String name, Object value)}
   * @param name The property name
   * @param value The property value
   */
  @JsonAnySetter
  void set(String name, Object value) {
    extraAttributes.put(name, value)
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

  void setTargetGroups() {
    Set<String> targetGroupNames = []
    def asg = getAsg()
    if (asg && asg.containsKey("targetGroupNames")) {
      targetGroupNames = (Set<String>) asg.targetGroupNames
    }
    this.targetGroups = targetGroupNames
  }

  @Override
  Set<String> getSecurityGroups() {
    Set<String> securityGroups = []
    if (launchConfig && launchConfig.containsKey("securityGroups")) {
      securityGroups = (Set<String>) launchConfig.securityGroups
    }

    RequestLaunchTemplateData launchTemplateData = null
    if (launchTemplate) {
      launchTemplateData = (RequestLaunchTemplateData) launchTemplate["launchTemplateData"]
    } else if (mixedInstancesPolicy) {
      launchTemplateData = (RequestLaunchTemplateData) mixedInstancesPolicy.launchTemplates[0]["launchTemplateData"]
    }
    if (launchTemplateData) {
      def securityGroupIds = (Set<String>) launchTemplateData?.securityGroupIds ?: []

      if (securityGroupIds?.size()) {
        securityGroups = (Set<String>) securityGroupIds
      }

      if (!securityGroupIds?.size()) {
        def networkInterface = launchTemplateData?.networkInterfaces?.find({ it["deviceIndex"] == 0 })
        def groups = networkInterface?.groups ?: []
        securityGroups = (Set<String>) groups
      }
    }

    return securityGroups
  }

  /**
   * Get a single instance type for a server group.
   * Used for the case of single instance type i.e. ASG with launch config, launch template, mixed instances policy without overrides
   *
   * For the case of multiple instance types i.e. ASG MixedInstancesPolicy with overrides, use mixedInstancesPolicy.allowedInstanceTypes.
   * @return a single instance type for ASG
   */
  @Override
  String getInstanceType() {
    if (launchConfig) {
      return launchConfig.instanceType
    } else if (launchTemplate) {
      return ((Map<String, Object>)launchTemplate.launchTemplateData).instanceType
    } else if (mixedInstancesPolicy) {
      if (!mixedInstancesPolicy.launchTemplateOverridesForInstanceType) {
        def mipLt = (Map<String, Object>) mixedInstancesPolicy.launchTemplates.get(0)
        return mipLt["launchTemplateData"]["instanceType"]
      }
      return null
    } else {
      return null
    }
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
  ServerGroup.ImagesSummary getImagesSummary() {
    def i = image
    def bi = buildInfo
    return new ServerGroup.ImagesSummary() {
      @Override
      List<? extends ServerGroup.ImageSummary> getSummaries() {
        return [new ServerGroup.ImageSummary() {
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
        }]
      }
    }
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    imagesSummary?.summaries?.get(0)
  }

  @Empty
  @JsonIgnore
  @Alpha
  Map getLaunchTemplateSpecification() {
    if (this.asg?.launchTemplate) {
      return this.asg.launchTemplate as Map
    } else if (this.asg?.mixedInstancesPolicy) {
      return this.asg.mixedInstancesPolicy["launchTemplate"]["launchTemplateSpecification"] as Map
    } else {
      return null
    }
  }

  static Collection<Instance> filterInstancesByHealthState(Collection<Instance> instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

  /**
   * MixedInstancesPolicySettings represents the configuration parameters for an ASG with mixed instances policy
   */
  static class MixedInstancesPolicySettings {
    /**
     * Instance types that the Amazon Server Group can realistically launch.
     *
     * If launchTemplateOverrides are specified, they will override the same properties in launch template e.g. instanceType
     * https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_LaunchTemplate.html
     */
    List<String> allowedInstanceTypes

    /**
     * Instances distribution configuration.
     */
    Map instancesDistribution

    /**
     * A list of launch template objects configured in the ASG.
     */
    List<Map> launchTemplates

    /**
     * A list of overrides, one for each instance type configured in the ASG.
     * Each override includes the information in LaunchTemplateOverrides https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_LaunchTemplateOverrides.html
     */
    List<Map> launchTemplateOverridesForInstanceType
  }
}
