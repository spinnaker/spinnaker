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
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import groovy.transform.Canonical

@Canonical
class GoogleLoadBalancer2 {

  String name
  String account
  String region
  Long createdTime
  String ipAddress
  String ipProtocol
  String portRange
  GoogleHealthCheck healthCheck
  List<GoogleLoadBalancerHealth> healths

  @JsonIgnore
  View getView() {
    new View()
  }

  class View implements LoadBalancer {
    final String type = "gce"

    String name = GoogleLoadBalancer2.this.name
    String account = GoogleLoadBalancer2.this.account
    String region = GoogleLoadBalancer2.this.region
    Long createdTime = GoogleLoadBalancer2.this.createdTime
    String ipAddress = GoogleLoadBalancer2.this.ipAddress
    String ipProtocol = GoogleLoadBalancer2.this.ipProtocol
    String portRange = GoogleLoadBalancer2.this.portRange
    GoogleHealthCheck.View healthCheck = GoogleLoadBalancer2.this.healthCheck?.view

    Set<Map<String, Object>> serverGroups = new HashSet<>()
  }
}
