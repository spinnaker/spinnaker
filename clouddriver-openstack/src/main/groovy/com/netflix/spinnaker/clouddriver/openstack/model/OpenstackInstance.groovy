/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.model

import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import groovy.transform.EqualsAndHashCode

import static org.openstack4j.model.compute.Server.Status

@EqualsAndHashCode
class OpenstackInstance implements Instance, Serializable {
  final String providerType = OpenstackCloudProvider.ID
  String name
  String instanceId
  long launchTime
  String zone
  String region
  String status
  String keyName
  String metadata

  //TODO - Determine if load balancers, security groups, and server groups are needed
  @Override
  Long getLaunchTime() {
    this.launchTime
  }

  // TODO - Determine which external health checks matter
  @Override
  List<Map<String, String>> getHealth() {
    [[
       state      : healthState.toString(),
       zone       : zone,
       type       : 'serverGroup',
       description: ''
     ]]
  }

  //TODO - Further define health states ... There are 18 OP and 7 spinnaker states.
  @Override
  HealthState getHealthState() {
    switch (Status.forValue(status)) {
      case Status.ACTIVE:
        HealthState.Up
        break
      case Status.BUILD:
        HealthState.Starting
        break
      case Status.UNKNOWN:
        HealthState.Unknown
        break
      default:
        HealthState.Down
    }
  }
}
