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
    new View()
  }

  class View extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType = GoogleSslLoadBalancer.this.type
    GoogleLoadBalancingScheme loadBalancingScheme = GoogleSslLoadBalancer.this.loadBalancingScheme

    String name = GoogleSslLoadBalancer.this.name
    String account = GoogleSslLoadBalancer.this.account
    String region = GoogleSslLoadBalancer.this.region
    Long createdTime = GoogleSslLoadBalancer.this.createdTime
    String ipAddress = GoogleSslLoadBalancer.this.ipAddress
    String ipProtocol = GoogleSslLoadBalancer.this.ipProtocol
    String portRange = GoogleSslLoadBalancer.this.portRange

    String certificate = GoogleSslLoadBalancer.this.certificate
    GoogleBackendService backendService = GoogleSslLoadBalancer.this.backendService

    Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  }
}
