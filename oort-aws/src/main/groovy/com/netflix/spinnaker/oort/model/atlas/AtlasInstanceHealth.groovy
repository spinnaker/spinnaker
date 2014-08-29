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

package com.netflix.spinnaker.oort.model.atlas

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthState
import groovy.transform.Immutable

@Immutable
class AtlasInstanceHealth implements Health {
  public static final String HEALTH_TYPE = 'Atlas'
  String type
  String asgName
  String instanceId
  String instanceState
  String ipAddress
  String publicIpAddress
  String availabilityZone
  boolean healthy
  DiscoveryHealth discoveryHealth
  List<Healthcheck> healthchecks
  List<LoadBalancerStatus> loadBalancers

  HealthState state
  String cluster

  @JsonCreator
  public static AtlasInstanceHealth createAtlasHealthInstance(@JsonProperty('cluster') String cluster,
                                                              @JsonProperty('asg') String asgName,
                                                              @JsonProperty('id') String instanceId,
                                                              @JsonProperty('state') String instanceState,
                                                              @JsonProperty('privateIpAddress') String ipAddress,
                                                              @JsonProperty('publicIpAddress') String publicIpAddress,
                                                              @JsonProperty('zone') String availabilityZone,
                                                              @JsonProperty('isHealthy') boolean healthy,
                                                              @JsonProperty('discovery') DiscoveryHealth discoveryHealth,
                                                              @JsonProperty('healthchecks') List<Healthcheck> healthchecks,
                                                              @JsonProperty('loadBalancers') List<LoadBalancerStatus> loadBalancers) {
    HealthState healthState = healthy ? HealthState.Up : HealthState.Down
    new AtlasInstanceHealth(HEALTH_TYPE, asgName, instanceId, instanceState, ipAddress, publicIpAddress, availabilityZone, healthy, discoveryHealth, healthchecks, loadBalancers, healthState, cluster)
  }
}

