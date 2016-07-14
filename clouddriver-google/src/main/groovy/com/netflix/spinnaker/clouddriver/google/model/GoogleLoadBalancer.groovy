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
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.Canonical

@Canonical
class GoogleLoadBalancer {

  String name
  String account
  String region
  Long createdTime
  String ipAddress
  String ipProtocol
  String portRange
  String targetPool
  GoogleHealthCheck healthCheck
  List<GoogleLoadBalancerHealth> healths

  @JsonIgnore
  View getView() {
    new View()
  }

  @Canonical
  class View implements LoadBalancer {
    final String type = GoogleCloudProvider.GCE

    String name = GoogleLoadBalancer.this.name
    String account = GoogleLoadBalancer.this.account
    String region = GoogleLoadBalancer.this.region
    Long createdTime = GoogleLoadBalancer.this.createdTime
    String ipAddress = GoogleLoadBalancer.this.ipAddress
    String ipProtocol = GoogleLoadBalancer.this.ipProtocol
    String portRange = GoogleLoadBalancer.this.portRange
    String targetPool =  GoogleLoadBalancer.this.targetPool
    GoogleHealthCheck.View healthCheck = GoogleLoadBalancer.this.healthCheck?.view

    Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  }
}
