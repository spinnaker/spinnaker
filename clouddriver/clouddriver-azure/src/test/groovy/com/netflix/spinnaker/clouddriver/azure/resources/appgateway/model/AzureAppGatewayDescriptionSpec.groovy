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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model

import com.azure.core.management.SubResource
import com.azure.resourcemanager.network.fluent.models.ApplicationGatewayInner
import com.azure.resourcemanager.network.fluent.models.ApplicationGatewayIpConfigurationInner
import com.azure.resourcemanager.network.fluent.models.ApplicationGatewayRequestRoutingRuleInner
import com.azure.resourcemanager.network.models.ApplicationGatewayBackendHttpSettings
import com.azure.resourcemanager.network.models.ApplicationGatewayFrontendIpConfiguration
import com.azure.resourcemanager.network.models.ApplicationGatewayFrontendPort
import com.azure.resourcemanager.network.models.ApplicationGatewayHttpListener
import com.azure.resourcemanager.network.models.ApplicationGatewayProtocol
import com.azure.resourcemanager.network.models.ApplicationGatewaySku
import com.azure.resourcemanager.network.models.ApplicationGatewaySkuName
import com.azure.resourcemanager.network.models.ApplicationGatewayTier
import org.mockito.Mockito
import spock.lang.Specification

class AzureAppGatewayDescriptionSpec extends Specification {

  static final String RESOURCE_GROUP = "my-rg"
  static final String AGW_NAME = "myapp-main-v001"
  static final String AGW_ID = "/subscriptions/sub1/resourceGroups/${RESOURCE_GROUP}/providers/Microsoft.Network/applicationGateways/${AGW_NAME}"
  static final String PUBLIC_IP_ID = "/subscriptions/sub1/resourceGroups/${RESOURCE_GROUP}/providers/Microsoft.Network/publicIPAddresses/myapp-pip"
  static final String SUBNET_ID = "/subscriptions/sub1/resourceGroups/${RESOURCE_GROUP}/providers/Microsoft.Network/virtualNetworks/myapp-vnet/subnets/default"

  /**
   * Build a minimal ApplicationGatewayInner mock with the required fields for
   * getDescriptionForAppGateway, arranging frontendIpConfigurations so that the
   * private frontend (null publicIpAddress) comes first in iteration order.
   * This mirrors the real Azure API behaviour where lex-sorted names place
   * "frontend-private" before "frontend-public".
   */
  private ApplicationGatewayInner buildAgwWithPrivateFrontendFirst() {
    def agw = Mockito.mock(ApplicationGatewayInner)

    // core identity
    Mockito.when(agw.name()).thenReturn(AGW_NAME)
    Mockito.when(agw.id()).thenReturn(AGW_ID)
    Mockito.when(agw.location()).thenReturn("westus")
    Mockito.when(agw.tags()).thenReturn([appName: "myapp", stack: "main", detail: null,
                                         cluster: null, trafficEnabledSG: null,
                                         hasNewSubnet: null, createdTime: null])

    // sku
    def sku = new ApplicationGatewaySku()
      .withName(ApplicationGatewaySkuName.STANDARD_SMALL)
      .withTier(ApplicationGatewayTier.STANDARD)
    Mockito.when(agw.sku()).thenReturn(sku)

    // routing rules null so the ?.first() safe-call short-circuits rather than blowing up on empty list
    Mockito.when(agw.requestRoutingRules()).thenReturn(null)
    Mockito.when(agw.backendAddressPools()).thenReturn([])
    Mockito.when(agw.probes()).thenReturn([])

    // gateway IP config (subnet reference)
    def subnetRef = new SubResource().withId(SUBNET_ID)
    def ipConfigInner = Mockito.mock(ApplicationGatewayIpConfigurationInner)
    Mockito.when(ipConfigInner.subnet()).thenReturn(subnetRef)
    Mockito.when(agw.gatewayIpConfigurations()).thenReturn([ipConfigInner])

    // frontend IP configurations: private first (no publicIpAddress), public second
    // Azure returns these in name-lexicographic order; "frontend-private" sorts before "frontend-public"
    def privateFrontend = new ApplicationGatewayFrontendIpConfiguration()
      .withName("frontend-private")
      // publicIpAddress intentionally NOT set → publicIpAddress() returns null

    def publicIpRef = new SubResource().withId(PUBLIC_IP_ID)
    def publicFrontend = new ApplicationGatewayFrontendIpConfiguration()
      .withName("frontend-public")
      .withPublicIpAddress(publicIpRef)

    Mockito.when(agw.frontendIpConfigurations()).thenReturn([privateFrontend, publicFrontend])

    agw
  }

  def 'getDescriptionForAppGateway picks the public frontend when private frontend comes first'() {
    given: 'a multi-frontend AGW where the private frontend is listed before the public one'
    def agw = buildAgwWithPrivateFrontendFirst()

    when:
    def description = AzureAppGatewayDescription.getDescriptionForAppGateway(agw)

    then: 'publicIpName resolves to the resource name from the public frontend IP'
    description.publicIpName == "myapp-pip"
  }

  def 'getDescriptionForAppGateway tolerates empty gatewayIpConfigurations'() {
    given: 'an AGW whose gatewayIpConfigurations list is empty'
    def agw = buildMinimalAgw()
    Mockito.when(agw.gatewayIpConfigurations()).thenReturn([])

    when:
    def description = AzureAppGatewayDescription.getDescriptionForAppGateway(agw)

    then: 'no exception is thrown and subnetResourceId is null'
    noExceptionThrown()
    description.subnetResourceId == null
  }

  def 'getDescriptionForAppGateway tolerates null requestRoutingRules'() {
    given: 'an AGW that returns null for requestRoutingRules'
    def agw = buildMinimalAgw()
    Mockito.when(agw.requestRoutingRules()).thenReturn(null)

    when:
    def description = AzureAppGatewayDescription.getDescriptionForAppGateway(agw)

    then: 'no exception is thrown and loadBalancingRules is empty'
    noExceptionThrown()
    description.loadBalancingRules.isEmpty()
  }

  def 'getDescriptionForAppGateway skips routing rule when httpListener reference is null'() {
    given: 'a rule with no httpListener reference but a non-empty listeners list'
    def agw = buildMinimalAgw()

    def someListenerId = "/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/httpListeners/listener1"
    def someListener = new ApplicationGatewayHttpListener()
      .withId(someListenerId)
      .withProtocol(ApplicationGatewayProtocol.HTTP)

    def rule = Mockito.mock(ApplicationGatewayRequestRoutingRuleInner)
    Mockito.when(rule.name()).thenReturn("rule1")
    Mockito.when(rule.httpListener()).thenReturn(null)

    Mockito.when(agw.requestRoutingRules()).thenReturn([rule])
    Mockito.when(agw.httpListeners()).thenReturn([someListener])
    Mockito.when(agw.frontendPorts()).thenReturn([])
    Mockito.when(agw.backendHttpSettingsCollection()).thenReturn([])

    when:
    def description = AzureAppGatewayDescription.getDescriptionForAppGateway(agw)

    then: 'no exception is thrown and no rule is added'
    noExceptionThrown()
    description.loadBalancingRules.isEmpty()
  }

  def 'getDescriptionForAppGateway tolerates null httpListeners'() {
    given: 'an AGW with a routing rule but null httpListeners'
    def agw = buildMinimalAgw()
    def listenerRef = new SubResource().withId("/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/httpListeners/listener1")
    def rule = Mockito.mock(ApplicationGatewayRequestRoutingRuleInner)
    Mockito.when(rule.name()).thenReturn("rule1")
    Mockito.when(rule.httpListener()).thenReturn(listenerRef)
    Mockito.when(agw.requestRoutingRules()).thenReturn([rule])
    Mockito.when(agw.httpListeners()).thenReturn(null)

    when:
    def description = AzureAppGatewayDescription.getDescriptionForAppGateway(agw)

    then: 'no exception is thrown and no rule is added'
    noExceptionThrown()
    description.loadBalancingRules.isEmpty()
  }

  def 'getDescriptionForAppGateway skips routing rule when frontendPort reference is null'() {
    given: 'a matched HTTP listener whose frontendPort reference is null but frontendPorts list is non-empty'
    def agw = buildMinimalAgw()
    def listenerId = "/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/httpListeners/listener1"
    def listenerRef = new SubResource().withId(listenerId)

    def listener = new ApplicationGatewayHttpListener()
      .withId(listenerId)
      .withProtocol(ApplicationGatewayProtocol.HTTP)
      // frontendPort intentionally not set → frontendPort() returns null

    def someFrontendPort = new ApplicationGatewayFrontendPort()
      .withId("/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/frontendPorts/port80")
      .withPort(80)

    def rule = Mockito.mock(ApplicationGatewayRequestRoutingRuleInner)
    Mockito.when(rule.name()).thenReturn("rule1")
    Mockito.when(rule.httpListener()).thenReturn(listenerRef)

    Mockito.when(agw.requestRoutingRules()).thenReturn([rule])
    Mockito.when(agw.httpListeners()).thenReturn([listener])
    Mockito.when(agw.frontendPorts()).thenReturn([someFrontendPort])
    Mockito.when(agw.backendHttpSettingsCollection()).thenReturn([])

    when:
    def description = AzureAppGatewayDescription.getDescriptionForAppGateway(agw)

    then: 'no exception is thrown and no rule is added'
    noExceptionThrown()
    description.loadBalancingRules.isEmpty()
  }

  def 'getDescriptionForAppGateway skips routing rule when backendHttpSettings reference is null'() {
    given: 'a matched HTTP listener with valid frontendPort but null backendHttpSettings on the rule'
    def agw = buildMinimalAgw()
    def listenerId = "/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/httpListeners/listener1"
    def listenerRef = new SubResource().withId(listenerId)
    def frontendPortId = "/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/frontendPorts/port80"
    def frontendPortRef = new SubResource().withId(frontendPortId)

    def listener = new ApplicationGatewayHttpListener()
      .withId(listenerId)
      .withProtocol(ApplicationGatewayProtocol.HTTP)
      .withFrontendPort(frontendPortRef)

    def frontendPort = new ApplicationGatewayFrontendPort()
      .withId(frontendPortId)
      .withPort(80)

    def someSettingsId = "/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/backendHttpSettingsCollection/settings1"
    def someSettings = new ApplicationGatewayBackendHttpSettings()
      .withId(someSettingsId)
      .withPort(8080)

    def rule = Mockito.mock(ApplicationGatewayRequestRoutingRuleInner)
    Mockito.when(rule.name()).thenReturn("rule1")
    Mockito.when(rule.httpListener()).thenReturn(listenerRef)
    Mockito.when(rule.backendHttpSettings()).thenReturn(null)

    Mockito.when(agw.requestRoutingRules()).thenReturn([rule])
    Mockito.when(agw.httpListeners()).thenReturn([listener])
    Mockito.when(agw.frontendPorts()).thenReturn([frontendPort])
    Mockito.when(agw.backendHttpSettingsCollection()).thenReturn([someSettings])

    when:
    def description = AzureAppGatewayDescription.getDescriptionForAppGateway(agw)

    then: 'no exception is thrown and no rule is added'
    noExceptionThrown()
    description.loadBalancingRules.isEmpty()
  }

  def 'getDescriptionForAppGateway maps a complete HTTP routing rule to a loadBalancingRule'() {
    given: 'a fully-populated AGW with one HTTP routing rule'
    def agw = buildMinimalAgw()
    def listenerId = "/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/httpListeners/listener1"
    def listenerRef = new SubResource().withId(listenerId)
    def frontendPortId = "/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/frontendPorts/port80"
    def frontendPortRef = new SubResource().withId(frontendPortId)
    def backendSettingsId = "/subscriptions/s/resourceGroups/rg/providers/Microsoft.Network/applicationGateways/agw/backendHttpSettingsCollection/settings1"
    def backendSettingsRef = new SubResource().withId(backendSettingsId)

    def listener = new ApplicationGatewayHttpListener()
      .withId(listenerId)
      .withProtocol(ApplicationGatewayProtocol.HTTP)
      .withFrontendPort(frontendPortRef)

    def frontendPort = new ApplicationGatewayFrontendPort()
      .withId(frontendPortId)
      .withPort(80)

    def backendSettings = new ApplicationGatewayBackendHttpSettings()
      .withId(backendSettingsId)
      .withPort(8080)

    def rule = Mockito.mock(ApplicationGatewayRequestRoutingRuleInner)
    Mockito.when(rule.name()).thenReturn("rule1")
    Mockito.when(rule.httpListener()).thenReturn(listenerRef)
    Mockito.when(rule.backendHttpSettings()).thenReturn(backendSettingsRef)

    Mockito.when(agw.requestRoutingRules()).thenReturn([rule])
    Mockito.when(agw.httpListeners()).thenReturn([listener])
    Mockito.when(agw.frontendPorts()).thenReturn([frontendPort])
    Mockito.when(agw.backendHttpSettingsCollection()).thenReturn([backendSettings])

    when:
    def description = AzureAppGatewayDescription.getDescriptionForAppGateway(agw)

    then: 'one loadBalancingRule is added with the correct ports'
    description.loadBalancingRules.size() == 1
    description.loadBalancingRules[0].ruleName == "rule1"
    description.loadBalancingRules[0].externalPort == 80
    description.loadBalancingRules[0].backendPort == 8080
  }

  // Helper: minimal AGW mock with no routing rules and a valid gatewayIpConfigurations list.
  // Tests that need to vary these properties override them after calling this helper.
  private ApplicationGatewayInner buildMinimalAgw() {
    def agw = Mockito.mock(ApplicationGatewayInner)
    Mockito.when(agw.name()).thenReturn(AGW_NAME)
    Mockito.when(agw.id()).thenReturn(AGW_ID)
    Mockito.when(agw.location()).thenReturn("westus")
    Mockito.when(agw.tags()).thenReturn([appName: "myapp", stack: "main", detail: null,
                                         cluster: null, trafficEnabledSG: null,
                                         hasNewSubnet: null, createdTime: null])

    def sku = new ApplicationGatewaySku()
      .withName(ApplicationGatewaySkuName.STANDARD_SMALL)
      .withTier(ApplicationGatewayTier.STANDARD)
    Mockito.when(agw.sku()).thenReturn(sku)

    def subnetRef = new SubResource().withId(SUBNET_ID)
    def ipConfigInner = Mockito.mock(ApplicationGatewayIpConfigurationInner)
    Mockito.when(ipConfigInner.subnet()).thenReturn(subnetRef)
    Mockito.when(agw.gatewayIpConfigurations()).thenReturn([ipConfigInner])

    Mockito.when(agw.requestRoutingRules()).thenReturn(null)
    Mockito.when(agw.backendAddressPools()).thenReturn([])
    Mockito.when(agw.frontendIpConfigurations()).thenReturn([])
    Mockito.when(agw.probes()).thenReturn([])

    agw
  }
}
