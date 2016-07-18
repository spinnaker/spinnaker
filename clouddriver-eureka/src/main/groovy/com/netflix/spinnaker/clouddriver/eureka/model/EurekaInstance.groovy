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

package com.netflix.spinnaker.clouddriver.eureka.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.model.DiscoveryHealth
import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable

@CompileStatic
@Immutable
@EqualsAndHashCode(cache = true)
class EurekaInstance extends DiscoveryHealth {
  @Override
  public static String getDiscoveryType() {
    return "Eureka"
  }

  String hostName
  String application
  String ipAddress
  String status
  String overriddenStatus
  HealthState state
  String eurekaStatus

  String accountId
  String availabilityZone
  String instanceId
  String amiId
  String instanceType

  String statusPageUrl
  String healthCheckUrl
  String vipAddress
  String secureVipAddress
  Long lastUpdatedTimestamp
  String asgName
  String titusTaskId

  @JsonCreator
  public static EurekaInstance buildInstance(@JsonProperty('hostName') String hostName,
                                             @JsonProperty('app') String app,
                                             @JsonProperty('ipAddr') String ipAddr,
                                             @JsonProperty('status') String status,
                                             @JsonProperty('overriddenstatus') String overriddenstatus,
                                             @JsonProperty('dataCenterInfo') DataCenterInfo dataCenterInfo,
                                             @JsonProperty('statusPageUrl') String statusPageUrl,
                                             @JsonProperty('healthCheckUrl') String healthCheckUrl,
                                             @JsonProperty('vipAddress') String vipAddress,
                                             @JsonProperty('secureVipAddress') String secureVipAddress,
                                             @JsonProperty('lastUpdatedTimestamp') long lastUpdatedTimestamp,
                                             @JsonProperty('asgName') String asgName,
                                             @JsonProperty('metadata') Metadata metadata) {
    def meta = dataCenterInfo.metadata
    final HealthState healthState
    if ('UP' == status) {
      healthState = HealthState.Up
    } else if ('STARTING' == status) {
      healthState = HealthState.Starting
    } else if ('UNKNOWN' == status) {
      healthState = HealthState.Unknown
    } else if ('OUT_OF_SERVICE' == status) {
      healthState = HealthState.OutOfService
    } else {
      healthState = HealthState.Down
    }
    new EurekaInstance(
      hostName,
      app,
      ipAddr,
      status,
      overriddenstatus,
      healthState,
      status,
      meta?.accountId,
      meta?.availabilityZone,
      meta?.instanceId,
      meta?.amiId,
      meta?.instanceType,
      statusPageUrl,
      healthCheckUrl,
      vipAddress,
      secureVipAddress,
      lastUpdatedTimestamp,
      asgName,
      metadata?.titusTaskId)
  }
}

