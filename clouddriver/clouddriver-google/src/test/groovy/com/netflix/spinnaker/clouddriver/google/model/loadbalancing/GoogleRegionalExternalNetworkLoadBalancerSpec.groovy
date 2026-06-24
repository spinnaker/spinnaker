/*
 * Copyright 2026 Harness, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

class GoogleRegionalExternalNetworkLoadBalancerSpec extends Specification {

  void "view exposes regional external network load balancer fields"() {
    given:
    def backendService = new GoogleBackendService(name: "regional-external-backend")
    def loadBalancer = new GoogleRegionalExternalNetworkLoadBalancer(
      name: "regional-external-nlb",
      account: "test-account",
      region: "us-central1",
      createdTime: 1234L,
      ipAddress: "35.1.2.3",
      ipProtocol: "TCP",
      ports: ["80", "443"],
      network: "projects/test/global/networks/default",
      networkTier: "PREMIUM",
      backendService: backendService
    )

    when:
    def view = loadBalancer.view

    then:
    view.loadBalancerType == GoogleLoadBalancerType.REGIONAL_EXTERNAL_NETWORK
    view.loadBalancingScheme == GoogleLoadBalancingScheme.EXTERNAL
    view.name == "regional-external-nlb"
    view.account == "test-account"
    view.region == "us-central1"
    view.ipAddress == "35.1.2.3"
    view.ipProtocol == "TCP"
    view.ports == ["80", "443"]
    view.network == "projects/test/global/networks/default"
    view.networkTier == "PREMIUM"
    view.backendService == backendService
  }

  void "cached attributes rehydrate into the regional external network model"() {
    given:
    def objectMapper = new ObjectMapper()
    def attributes = [
      type               : "REGIONAL_EXTERNAL_NETWORK",
      loadBalancingScheme: "EXTERNAL",
      name               : "regional-external-nlb",
      account            : "test-account",
      region             : "us-central1",
      ipProtocol         : "UDP",
      ports              : ["53"],
      networkTier        : "STANDARD",
      backendService     : [name: "regional-external-backend"]
    ]

    when:
    def loadBalancer = objectMapper.convertValue(attributes, GoogleRegionalExternalNetworkLoadBalancer)

    then:
    loadBalancer.type == GoogleLoadBalancerType.REGIONAL_EXTERNAL_NETWORK
    loadBalancer.loadBalancingScheme == GoogleLoadBalancingScheme.EXTERNAL
    loadBalancer.view.backendService.name == "regional-external-backend"
    loadBalancer.view.networkTier == "STANDARD"
  }
}
