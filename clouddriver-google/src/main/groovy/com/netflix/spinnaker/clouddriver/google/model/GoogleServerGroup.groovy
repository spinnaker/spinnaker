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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.api.services.compute.model.InstanceGroupManagerActionsSummary
import com.google.api.services.compute.model.InstanceGroupManagerAutoHealingPolicy
import com.google.api.services.compute.model.ServiceAccount
import com.google.api.services.compute.model.StatefulPolicy
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.Canonical

import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.GLOBAL_LOAD_BALANCER_NAMES
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.LOAD_BALANCING_POLICY
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.REGIONAL_LOAD_BALANCER_NAMES

@Canonical
class GoogleServerGroup implements GoogleLabeledResource {

  String name
  String region
  String account
  Boolean regional = false
  String zone
  Set<String> zones = new HashSet<>()
  Set<GoogleInstance> instances = []
  Set health = []
  Map<String, Object> launchConfig = [:]
  Map<String, Object> asg = [:]
  Map<String, Integer> namedPorts = [:]
  Set<String> securityGroups = []
  Map buildInfo
  Boolean disabled = false
  Boolean discovery = false
  String networkName
  Boolean canIpForward = false
  Boolean enableSecureBoot = false
  Boolean enableVtpm = false
  Boolean enableIntegrityMonitoring = false
  Set<String> instanceTemplateTags = []
  Set<ServiceAccount> instanceTemplateServiceAccounts = []
  Map<String, String> instanceTemplateLabels = [:]
  String selfLink
  InstanceGroupManagerActionsSummary currentActions
  /**
   * Optional explicit specification of zones for an RMIG.
   */
  GoogleDistributionPolicy distributionPolicy
  Boolean selectZones

  GoogleAutoscalingPolicy autoscalingPolicy

  @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="class")
  StatefulPolicy statefulPolicy

  List<String> autoscalingMessages
  //Map<String, String> scalingSchedulingMessages

  @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="class")
  InstanceGroupManagerAutoHealingPolicy autoHealingPolicy

  // Non-serialized values built up by providers
  @JsonIgnore
  Set<GoogleLoadBalancerView> loadBalancers = []

  @JsonIgnore
  View getView() {
    new View(this)
  }

  @Override
  @JsonIgnore
  Map<String, String> getLabels() {
    return instanceTemplateLabels
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Canonical
  class View implements ServerGroup {
    final String type = GoogleCloudProvider.ID
    final String cloudProvider = GoogleCloudProvider.ID

    String name
    String region
    Boolean regional
    String zone
    Set<String> zones
    Set<GoogleInstance.View> instances
    Map<String, Object> asg
    Map<String, Object> launchConfig
    Map<String, Integer> namedPorts
    Set<String> securityGroups
    Map buildInfo
    Boolean disabled
    String networkName
    Boolean canIpForward
    Boolean enableSecureBoot
    Boolean enableVtpm
    Boolean enableIntegrityMonitoring
    Set<String> instanceTemplateTags
    Set<ServiceAccount> instanceTemplateServiceAccounts
    Map<String, String> instanceTemplateLabels
    String selfLink
    Boolean discovery
    InstanceGroupManagerActionsSummary currentActions
    GoogleAutoscalingPolicy autoscalingPolicy
    StatefulPolicy statefulPolicy
    List<String> autoscalingMessages
    InstanceGroupManagerAutoHealingPolicy autoHealingPolicy
    GoogleDistributionPolicy distributionPolicy
    Boolean selectZones

    View(GoogleServerGroup googleServerGroup){
      name = googleServerGroup.name
      region = googleServerGroup.region
      regional = googleServerGroup.regional
      zone = googleServerGroup.zone
      zones = googleServerGroup.zones
      instances = googleServerGroup.instances.collect { it?.view }
      asg = googleServerGroup.asg
      launchConfig = googleServerGroup.launchConfig
      namedPorts = googleServerGroup.namedPorts
      securityGroups = googleServerGroup.securityGroups
      buildInfo = googleServerGroup.buildInfo
      disabled = googleServerGroup.disabled
      networkName = googleServerGroup.networkName
      canIpForward = googleServerGroup.canIpForward
      enableSecureBoot = googleServerGroup.enableSecureBoot
      enableVtpm = googleServerGroup.enableVtpm
      enableIntegrityMonitoring = googleServerGroup.enableIntegrityMonitoring
      instanceTemplateTags = googleServerGroup.instanceTemplateTags
      instanceTemplateServiceAccounts = googleServerGroup.instanceTemplateServiceAccounts
      instanceTemplateLabels = googleServerGroup.instanceTemplateLabels
      selfLink = googleServerGroup.selfLink
      discovery = googleServerGroup.discovery
      currentActions = googleServerGroup.currentActions
      autoscalingPolicy = googleServerGroup.autoscalingPolicy
      statefulPolicy = googleServerGroup.statefulPolicy
      autoscalingMessages = googleServerGroup.autoscalingMessages
      autoHealingPolicy = googleServerGroup.autoHealingPolicy
      distributionPolicy = googleServerGroup.distributionPolicy
      selectZones = googleServerGroup.selectZones
    }

    @Override
    Moniker getMoniker() {
      return NamerRegistry.lookup()
        .withProvider(GoogleCloudProvider.ID)
        .withAccount(account)
        .withResource(GoogleLabeledResource)
        .deriveMoniker(GoogleServerGroup.this)
    }

    @Override
    Long getCreatedTime() {
      launchConfig ? launchConfig.createdTime as Long : null
    }

    @Override
    ServerGroup.Capacity getCapacity() {
      def asg = GoogleServerGroup.this.asg
      asg ?
        new ServerGroup.Capacity(min: asg.minSize ? asg.minSize as Integer : 0,
          max: asg.maxSize ? asg.maxSize as Integer : 0,
          desired: asg.desiredCapacity ? asg.desiredCapacity as Integer : 0) :
        null
    }

    /**
     * @return The load balancing policy containing the capacity metrics and named ports this server
     * group is configured with for all L7 backends.
     *
     * This is intended to to be used as the suggestion in the server group wizard for load balancing policy.
     */
    GoogleHttpLoadBalancingPolicy getLoadBalancingPolicy() {
      return GoogleServerGroup.this.asg?.get(LOAD_BALANCING_POLICY) as GoogleHttpLoadBalancingPolicy
    }

    @Override
    Set<String> getLoadBalancers() {
      Set<String> loadBalancerNames = []
      def asg = GoogleServerGroup.this.asg
      if (asg?.containsKey(REGIONAL_LOAD_BALANCER_NAMES)) {
        loadBalancerNames.addAll(asg.get(REGIONAL_LOAD_BALANCER_NAMES) as Set<String>)
      }
      if (asg?.containsKey(GLOBAL_LOAD_BALANCER_NAMES)) {
        loadBalancerNames.addAll(asg.get(GLOBAL_LOAD_BALANCER_NAMES) as Set<String>)
      }
      return loadBalancerNames
    }

    @Override
    ServerGroup.ImagesSummary getImagesSummary() {
      def bi = GoogleServerGroup.this.buildInfo
      return new ServerGroup.ImagesSummary() {
        @Override
        List<ServerGroup.ImageSummary> getSummaries() {
          return [new ServerGroup.ImageSummary() {
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
          }]
        }
      }
    }

    @Override
    ServerGroup.ImageSummary getImageSummary() {
      imagesSummary?.summaries?.get(0)
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

    // Cloud provider-specific metadata that is available from the server group list endpoint.
    Map getProviderMetadata() {
      [
        tags: GoogleServerGroup.this.launchConfig?.instanceTemplate?.properties?.tags?.items,
        serviceAccounts: GoogleServerGroup.this.launchConfig?.instanceTemplate?.properties?.serviceAccounts,
        networkName: GoogleServerGroup.this.networkName
      ]
    }

    Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
      instances.findAll { Instance it -> it.getHealthState() == healthState }
    }
  }

  static enum ServerGroupType {
    REGIONAL,
    ZONAL
  }
}
