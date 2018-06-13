/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import groovy.transform.Canonical

@Canonical
@JsonIgnoreProperties(ignoreUnknown = true)
class OracleServerGroup {

  String name
  String region
  String zone
  Set<String> zones = new HashSet<>()
  Set<OracleInstance> instances = []
  Map<String, Object> launchConfig = [:]
  Set<String> securityGroups = []
  Map buildInfo
  Boolean disabled = false
  Integer targetSize
  String loadBalancerId
  OracleNamedAccountCredentials credentials

  @JsonIgnore
  View getView() {
    new View()
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Canonical
  class View implements ServerGroup {

    final String type = OracleCloudProvider.ID
    final String cloudProvider = OracleCloudProvider.ID

    String name = OracleServerGroup.this.name
    String region = OracleServerGroup.this.region
    String zone = OracleServerGroup.this.zone
    Set<String> zones = OracleServerGroup.this.zones
    Set<OracleInstance> instances = OracleServerGroup.this.instances
    Map<String, Object> launchConfig = OracleServerGroup.this.launchConfig
    Set<String> securityGroups = OracleServerGroup.this.securityGroups
    Map buildInfo = OracleServerGroup.this.buildInfo
    Boolean disabled = OracleServerGroup.this.disabled
    ServerGroup.Capacity capacity = new ServerGroup.Capacity(desired: OracleServerGroup.this.targetSize,
      min: OracleServerGroup.this.targetSize, max: OracleServerGroup.this.targetSize)

    @Override
    Boolean isDisabled() { // Because groovy isn't smart enough to generate this method :-(
      disabled
    }

    @Override
    Long getCreatedTime() {
      launchConfig ? launchConfig.createdTime as Long : null
    }

    @Override
    Set<String> getLoadBalancers() {
      return [OracleServerGroup.this.loadBalancerId] as Set
    }

    @Override
    ServerGroup.Capacity getCapacity() {
      capacity
    }

    @Override
    ServerGroup.ImagesSummary getImagesSummary() {
      def bi = OracleServerGroup.this.buildInfo
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

    static Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
      instances.findAll { Instance it -> it.getHealthState() == healthState }
    }
  }

}
