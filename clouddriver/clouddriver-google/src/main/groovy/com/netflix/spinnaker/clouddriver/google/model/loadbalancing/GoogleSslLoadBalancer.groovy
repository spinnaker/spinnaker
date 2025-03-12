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

package com.netflix.spinnaker.clouddriver.google.model.loadbalancing

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeSuper=true)
@EqualsAndHashCode(callSuper=true)
class GoogleSslLoadBalancer extends GoogleLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.SSL
  GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.EXTERNAL

  String certificate
  GoogleBackendService backendService

  @JsonIgnore
  GoogleLoadBalancerView getView() {
    new View(this)
  }

  class View extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType
    GoogleLoadBalancingScheme loadBalancingScheme

    String name
    String account
    String region
    Long createdTime
    String ipAddress
    String ipProtocol
    String portRange

    String certificate
    GoogleBackendService backendService

    Set<LoadBalancerServerGroup> serverGroups

    View(GoogleSslLoadBalancer googleSslLoadBalancer){
      loadBalancerType = googleSslLoadBalancer.type
      loadBalancingScheme = googleSslLoadBalancer.loadBalancingScheme
      name = googleSslLoadBalancer.name
      account = googleSslLoadBalancer.account
      region = googleSslLoadBalancer.region
      createdTime = googleSslLoadBalancer.createdTime
      ipAddress = googleSslLoadBalancer.ipAddress
      ipProtocol = googleSslLoadBalancer.ipProtocol
      portRange = googleSslLoadBalancer.portRange
      certificate = googleSslLoadBalancer.certificate
      backendService = googleSslLoadBalancer.backendService
      serverGroups = new HashSet<>()
    }

  }
}
