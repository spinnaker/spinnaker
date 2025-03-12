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
  public String getDiscoveryType() {
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
  String titusStack

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
                                             @JsonProperty('metadata') Metadata metadata,
                                             @JsonProperty('instanceId') String registrationInstanceId) {
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

    // if this has an asgName and is not part of a titus task registration,
    // prefer the app name derived from the asg name rather than the supplied
    // app name. We index these records on application to associate them to
    // a particular cluster, and with the name incorrect then the record will
    // not be properly linked
    if (metadata?.titusTaskId == null && asgName != null) {
      def idx = asgName.indexOf('-')
      def appFromAsg = idx == -1 ? asgName : asgName.substring(0, idx)
      app = appFromAsg
    }

    //the preferred instanceId value comes from DataCenterInfo Metadata
    // Jackson was doing some shenanigans whereby the top level registration
    // instanceId would overwrite the value from DataCenterInfo because it
    // was not defined as a @JsonProperty on this @JsonCreator
    //
    //This will now prefer and use the instanceId from metadata if present
    // but fall back to the registration level instance id for anyone who
    // was possibly never registering with a DataCenterInfo Metadata entry.
    String instanceId = meta?.instanceId ?: registrationInstanceId
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
      instanceId,
      meta?.amiId,
      meta?.instanceType,
      statusPageUrl,
      healthCheckUrl,
      vipAddress,
      secureVipAddress,
      lastUpdatedTimestamp,
      asgName,
      metadata?.titusTaskId,
      metadata?.titusStack)
  }
}

