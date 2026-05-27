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
import com.azure.resourcemanager.network.models.ApplicationGatewayFrontendIpConfiguration
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
}
