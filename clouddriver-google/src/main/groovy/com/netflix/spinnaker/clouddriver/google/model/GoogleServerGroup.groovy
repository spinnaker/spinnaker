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
import com.google.api.services.compute.model.AutoscalingPolicy
import com.google.api.services.compute.model.InstanceGroupManagerActionsSummary
import com.google.api.services.compute.model.InstanceGroupManagerAutoHealingPolicy
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
  Set<String> instanceTemplateServiceAccounts = []
  Map<String, String> instanceTemplateLabels = [:]
  String selfLink
  InstanceGroupManagerActionsSummary currentActions
  /**
   * Optional explicit specification of zones for an RMIG.
   */
  GoogleDistributionPolicy distributionPolicy
  Boolean selectZones

  @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="class")
  AutoscalingPolicy autoscalingPolicy

  List<String> autoscalingMessages

  @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="class")
  InstanceGroupManagerAutoHealingPolicy autoHealingPolicy

  // Non-serialized values built up by providers
  @JsonIgnore
  Set<GoogleLoadBalancerView> loadBalancers = []

  @JsonIgnore
  View getView() {
    new View()
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

    String name = GoogleServerGroup.this.name
    String region = GoogleServerGroup.this.region
    Boolean regional = GoogleServerGroup.this.regional
    String zone = GoogleServerGroup.this.zone
    Set<String> zones = GoogleServerGroup.this.zones
    Set<GoogleInstance.View> instances = GoogleServerGroup.this.instances.collect { it?.view }
    Map<String, Object> asg = GoogleServerGroup.this.asg
    Map<String, Object> launchConfig = GoogleServerGroup.this.launchConfig
    Map<String, Integer> namedPorts = GoogleServerGroup.this.namedPorts
    Set<String> securityGroups = GoogleServerGroup.this.securityGroups
    Map buildInfo = GoogleServerGroup.this.buildInfo
    Boolean disabled = GoogleServerGroup.this.disabled
    String networkName = GoogleServerGroup.this.networkName
    Boolean canIpForward = GoogleServerGroup.this.canIpForward
    Boolean enableSecureBoot = GoogleServerGroup.this.enableSecureBoot
    Boolean enableVtpm = GoogleServerGroup.this.enableVtpm
    Boolean enableIntegrityMonitoring = GoogleServerGroup.this.enableIntegrityMonitoring
    Set<String> instanceTemplateTags = GoogleServerGroup.this.instanceTemplateTags
    Set<String> instanceTemplateServiceAccounts = GoogleServerGroup.this.instanceTemplateServiceAccounts
    Map<String, String> instanceTemplateLabels = GoogleServerGroup.this.instanceTemplateLabels
    String selfLink = GoogleServerGroup.this.selfLink
    Boolean discovery = GoogleServerGroup.this.discovery
    InstanceGroupManagerActionsSummary currentActions = GoogleServerGroup.this.currentActions
    AutoscalingPolicy autoscalingPolicy = GoogleServerGroup.this.autoscalingPolicy
    List<String> autoscalingMessages = GoogleServerGroup.this.autoscalingMessages
    InstanceGroupManagerAutoHealingPolicy autoHealingPolicy = GoogleServerGroup.this.autoHealingPolicy
    GoogleDistributionPolicy distributionPolicy = GoogleServerGroup.this.distributionPolicy
    Boolean selectZones = GoogleServerGroup.this.selectZones

    @Override
    Moniker getMoniker() {
      return NamerRegistry.lookup()
        .withProvider(GoogleCloudProvider.ID)
        .withAccount(account)
        .withResource(GoogleLabeledResource)
        .deriveMoniker(GoogleServerGroup.this)
    }

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
