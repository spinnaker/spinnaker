/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.model

import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "account"])
class AppengineServerGroup implements ServerGroup, Serializable {
  String name
  final String type = AppengineCloudProvider.ID
  final String cloudProvider = AppengineCloudProvider.ID
  String account
  String region
  Set<String> zones = []
  Set<AppengineInstance> instances
  Set<String> loadBalancers = []
  Long createdTime
  Map<String, Object> launchConfig = [:]
  Set<String> securityGroups = []
  Boolean disabled = true
  AppengineScalingPolicy scalingPolicy
  ServingStatus servingStatus
  Environment env
  String httpUrl
  String httpsUrl
  String instanceClass
  Boolean allowsGradualTrafficMigration

  AppengineServerGroup() {}

  AppengineServerGroup(Version version, String account, String region, String loadBalancerName, Boolean isDisabled) {
    this.account = account
    this.region = region
    this.name = version.getId()
    this.loadBalancers = [loadBalancerName] as Set
    this.createdTime = AppengineModelUtil.translateTime(version.getCreateTime())
    this.disabled = isDisabled
    this.scalingPolicy = AppengineModelUtil.getScalingPolicy(version)
    this.servingStatus = version.getServingStatus() ? ServingStatus.valueOf(version.getServingStatus()) : null
    this.env = version.getEnv() ? Environment.valueOf(version.getEnv().toUpperCase()) : null
    this.httpUrl = AppengineModelUtil.getHttpUrl(version.getName())
    this.httpsUrl = AppengineModelUtil.getHttpsUrl(version.getName())
    this.instanceClass = version.getInstanceClass()
    this.launchConfig.instanceType = this.instanceClass
    this.zones = [region] as Set
    this.allowsGradualTrafficMigration = versionAllowsGradualTrafficMigration(version)
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    new ServerGroup.InstanceCounts(
      down: 0,
      outOfService: (Integer) instances?.count { it.healthState == HealthState.OutOfService } ?: 0,
      up: (Integer) instances?.count { it.healthState == HealthState.Up } ?: 0,
      starting: 0,
      unknown: 0,
      total: (Integer) instances?.size(),
    )
  }

  def update(Version version, String account, String region, String loadBalancerName, Boolean isDisabled) {
    this.account = account
    this.region = region
    this.name = version.getId()
    this.loadBalancers.add(loadBalancerName)
    this.createdTime = AppengineModelUtil.translateTime(version.getCreateTime())
    this.disabled = isDisabled
    this.scalingPolicy = AppengineModelUtil.getScalingPolicy(version)
    this.servingStatus = version.getServingStatus() ? ServingStatus.valueOf(version.getServingStatus()) : null
    this.env = version.getEnv() ? Environment.valueOf(version.getEnv().toUpperCase()) : null
    this.httpUrl = AppengineModelUtil.getHttpUrl(version.getName())
    this.httpsUrl = AppengineModelUtil.getHttpsUrl(version.getName())
    this.instanceClass = version.getInstanceClass()
    this.launchConfig.instanceType = this.instanceClass
    this.zones = [region] as Set
    this.allowsGradualTrafficMigration = versionAllowsGradualTrafficMigration(version)
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    Integer instanceCount = instances?.size() ?: 0

    switch (scalingPolicy?.type) {
      case ScalingPolicyType.AUTOMATIC:
        /*
        * For the flexible environment, a version using automatic scaling can be stopped.
        * A stopped version scales down to zero instances and ignores its scaling policy.
        * */
        def min = computeMinForAutomaticScaling(scalingPolicy)
        def max = computeMaxForAutomaticScaling(scalingPolicy)
        return new ServerGroup.Capacity(min: min,
                                        max: max,
                                        desired: min)
        break
      case ScalingPolicyType.BASIC:
        def desired = servingStatus == ServingStatus.SERVING ? instanceCount : 0
        return new ServerGroup.Capacity(min: 0,
                                        max: scalingPolicy.maxInstances,
                                        desired: desired)
        break
      case ScalingPolicyType.MANUAL:
        def desired = servingStatus == ServingStatus.SERVING ? scalingPolicy.instances : 0
        return new ServerGroup.Capacity(min: 0,
                                        max: scalingPolicy.instances,
                                        desired: desired)
        break
      default:
        return new ServerGroup.Capacity(min: instanceCount, max: instanceCount, desired: instanceCount)
        break
    }
  }

  Integer computeMinForAutomaticScaling(AppengineScalingPolicy scalingPolicy) {
    Integer instanceCount = instances?.size() ?: 0
    if (servingStatus == ServingStatus.SERVING) {
      def candidateMinValues = [scalingPolicy.minIdleInstances, scalingPolicy.minTotalInstances, instanceCount].findAll { it != null }
      return candidateMinValues.min()
    } else {
      return 0
    }
  }

  Integer computeMaxForAutomaticScaling(AppengineScalingPolicy scalingPolicy) {
    Integer instanceCount = instances?.size() ?: 0
    if (servingStatus == ServingStatus.SERVING) {
      def candidateMaxValues = [scalingPolicy.maxIdleInstances, scalingPolicy.maxTotalInstances, instanceCount].findAll { it != null }
      return candidateMaxValues.max()
    } else {
      return instanceCount
    }
  }

  static Boolean versionAllowsGradualTrafficMigration(Version version) {
    // Versions do not always have an env property if they are in the standard environment.
    def inStandardEnvironment = version.getEnv()?.toUpperCase() != "FLEXIBLE"
    def warmupRequestsConfigured = (version.getInboundServices() ?: []).contains("INBOUND_SERVICE_WARMUP")
    def usesAutomaticScaling = AppengineModelUtil.getScalingPolicy(version).type == ScalingPolicyType.AUTOMATIC
    return inStandardEnvironment && warmupRequestsConfigured && usesAutomaticScaling
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    null
  }

  @Override
  ServerGroup.ImagesSummary getImagesSummary() {
    null
  }

  @Override
  Boolean isDisabled() {
    disabled
  }

  enum ServingStatus {
    SERVING,
    STOPPED,
  }

  enum Environment {
    STANDARD,
    FLEXIBLE,
  }
}
