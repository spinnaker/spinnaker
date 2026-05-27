/*
 * Copyright 2024 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model

import com.azure.core.management.SubResource
import com.azure.resourcemanager.network.fluent.models.InboundNatRuleInner
import com.azure.resourcemanager.network.fluent.models.LoadBalancerInner
import com.azure.resourcemanager.network.fluent.models.LoadBalancingRuleInner
import com.azure.resourcemanager.network.fluent.models.ProbeInner
import com.azure.resourcemanager.network.models.ProbeProtocol
import com.azure.resourcemanager.network.models.TransportProtocol
import org.mockito.Mockito
import spock.lang.Specification

class AzureLoadBalancerDescriptionSpec extends Specification {

  static final String LB_NAME = "myapp-main-v001"
  static final String LB_ID = "/subscriptions/sub1/resourceGroups/my-rg/providers/Microsoft.Network/loadBalancers/${LB_NAME}"

  def 'build tolerates null loadBalancingRules'() {
    given: 'a load balancer whose loadBalancingRules() returns null'
    def lb = buildMinimalLb()
    Mockito.when(lb.loadBalancingRules()).thenReturn(null)

    when:
    def description = AzureLoadBalancerDescription.build(lb)

    then: 'no exception is thrown and loadBalancingRules is empty'
    noExceptionThrown()
    description.loadBalancingRules.isEmpty()
  }

  def 'build tolerates a routing rule with null backendAddressPool'() {
    given: 'a rule that has no backendAddressPool reference'
    def lb = buildMinimalLb()
    def rule = Mockito.mock(LoadBalancingRuleInner)
    Mockito.when(rule.name()).thenReturn("rule1")
    Mockito.when(rule.frontendPort()).thenReturn(80)
    Mockito.when(rule.backendPort()).thenReturn(8080)
    Mockito.when(rule.probe()).thenReturn(null)
    Mockito.when(rule.loadDistribution()).thenReturn(null)
    Mockito.when(rule.idleTimeoutInMinutes()).thenReturn(4)
    Mockito.when(rule.backendAddressPool()).thenReturn(null)
    Mockito.when(rule.protocol()).thenReturn(TransportProtocol.TCP)
    Mockito.when(lb.loadBalancingRules()).thenReturn([rule])

    when:
    def description = AzureLoadBalancerDescription.build(lb)

    then: 'no exception is thrown and the rule is still added (trafficEnabledSG is null)'
    noExceptionThrown()
    description.loadBalancingRules.size() == 1
    description.trafficEnabledSG == null
  }

  def 'build tolerates null probes'() {
    given: 'a load balancer whose probes() returns null'
    def lb = buildMinimalLb()
    Mockito.when(lb.probes()).thenReturn(null)

    when:
    def description = AzureLoadBalancerDescription.build(lb)

    then: 'no exception is thrown and probes is empty'
    noExceptionThrown()
    description.probes.isEmpty()
  }

  def 'build tolerates null inboundNatRules'() {
    given: 'a load balancer whose inboundNatRules() returns null'
    def lb = buildMinimalLb()
    Mockito.when(lb.inboundNatRules()).thenReturn(null)

    when:
    def description = AzureLoadBalancerDescription.build(lb)

    then: 'no exception is thrown and inboundNATRules is empty'
    noExceptionThrown()
    description.inboundNATRules.isEmpty()
  }

  def 'build maps a complete load balancer to a description'() {
    given: 'a fully-populated load balancer with one rule, one probe, one NAT rule'
    def lb = buildMinimalLb()

    def backendPoolRef = new SubResource().withId(
      "/subscriptions/sub1/resourceGroups/my-rg/providers/Microsoft.Network/loadBalancers/${LB_NAME}/backendAddressPools/myBackendPool")
    def rule = Mockito.mock(LoadBalancingRuleInner)
    Mockito.when(rule.name()).thenReturn("rule1")
    Mockito.when(rule.frontendPort()).thenReturn(80)
    Mockito.when(rule.backendPort()).thenReturn(8080)
    Mockito.when(rule.probe()).thenReturn(null)
    Mockito.when(rule.loadDistribution()).thenReturn(null)
    Mockito.when(rule.idleTimeoutInMinutes()).thenReturn(4)
    Mockito.when(rule.backendAddressPool()).thenReturn(backendPoolRef)
    Mockito.when(rule.protocol()).thenReturn(TransportProtocol.TCP)
    Mockito.when(lb.loadBalancingRules()).thenReturn([rule])

    def probe = Mockito.mock(ProbeInner)
    Mockito.when(probe.name()).thenReturn("probe1")
    Mockito.when(probe.port()).thenReturn(8080)
    Mockito.when(probe.intervalInSeconds()).thenReturn(10)
    Mockito.when(probe.numberOfProbes()).thenReturn(3)
    Mockito.when(probe.requestPath()).thenReturn("/health")
    Mockito.when(probe.protocol()).thenReturn(ProbeProtocol.HTTP)
    Mockito.when(lb.probes()).thenReturn([probe])

    def natRule = Mockito.mock(InboundNatRuleInner)
    Mockito.when(natRule.name()).thenReturn("nat1")
    Mockito.when(lb.inboundNatRules()).thenReturn([natRule])

    when:
    def description = AzureLoadBalancerDescription.build(lb)

    then: 'all collections are populated correctly'
    description.loadBalancingRules.size() == 1
    description.loadBalancingRules[0].ruleName == "rule1"
    description.probes.size() == 1
    description.probes[0].probeName == "probe1"
    description.inboundNATRules.size() == 1
    description.inboundNATRules[0].ruleName == "nat1"
    description.trafficEnabledSG == "myBackendPool"
  }

  // Helper: minimal LoadBalancerInner mock with empty collections and basic tags.
  private LoadBalancerInner buildMinimalLb() {
    def lb = Mockito.mock(LoadBalancerInner)
    Mockito.when(lb.name()).thenReturn(LB_NAME)
    Mockito.when(lb.id()).thenReturn(LB_ID)
    Mockito.when(lb.location()).thenReturn("westus")
    Mockito.when(lb.tags()).thenReturn([appName: "myapp", stack: "main", detail: null,
                                        cluster: null, vnet: null, createdTime: null,
                                        internal: null])
    Mockito.when(lb.frontendIpConfigurations()).thenReturn(null)
    Mockito.when(lb.backendAddressPools()).thenReturn([])
    Mockito.when(lb.loadBalancingRules()).thenReturn([])
    Mockito.when(lb.probes()).thenReturn([])
    Mockito.when(lb.inboundNatRules()).thenReturn([])
    lb
  }
}
