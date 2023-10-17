/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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
  String backendSetName
  OracleNamedAccountCredentials credentials

  @JsonIgnore
  View getView() {
    new View(this)
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Canonical
  class View implements ServerGroup {

    final String type = OracleCloudProvider.ID
    final String cloudProvider = OracleCloudProvider.ID

    String name
    String region
    String zone
    Set<String> zones
    Set<OracleInstance> instances
    Map<String, Object> launchConfig
    Set<String> securityGroups
    Map buildInfo
    Boolean disabled
    ServerGroup.Capacity capacity

    View(OracleServerGroup oracleServerGroup){
      name = oracleServerGroup.name
      region = oracleServerGroup.region
      zone = oracleServerGroup.zone
      zones = oracleServerGroup.zones
      instances = oracleServerGroup.instances
      launchConfig = oracleServerGroup.launchConfig
      securityGroups = oracleServerGroup.securityGroups
      buildInfo = oracleServerGroup.buildInfo
      disabled = oracleServerGroup.disabled
      capacity = new ServerGroup.Capacity(desired: oracleServerGroup.targetSize,
        min: oracleServerGroup.targetSize, max: oracleServerGroup.targetSize)
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
      return new ServerGroup.ImagesSummary() {

        @Override
        List<ServerGroup.ImageSummary> getSummaries() {
          return listSummaries()
        }
      }
    }

    List<ServerGroup.ImageSummary> listSummaries() {
      def bi = OracleServerGroup.this.buildInfo
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

    @Override
    ServerGroup.ImageSummary getImageSummary() {
      imagesSummary?.summaries?.get(0)
    }

    @Override
    ServerGroup.InstanceCounts getInstanceCounts() {
      new ServerGroup.InstanceCounts(
        total: instances.size(),
        up: sizeOf(instances, HealthState.Up),
        down: sizeOf(instances, HealthState.Down),
        unknown: sizeOf(instances, HealthState.Unknown),
        starting: sizeOf(instances, HealthState.Starting),
        outOfService: sizeOf(instances, HealthState.OutOfService)
      )
    }

    int sizeOf(Set<Instance> instances, HealthState healthState) {
      instances.findAll { Instance it -> it.getHealthState() == healthState }?.size() ?: 0
    }
  }
}
