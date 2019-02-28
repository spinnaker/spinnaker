/*
 * Copyright 2016 The original authors.
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

import com.microsoft.azure.management.network.ApplicationGatewayProtocol
import com.microsoft.azure.management.network.implementation.ApplicationGatewayInner
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureAppGatewayResourceTemplate

class AzureAppGatewayDescription extends AzureResourceOpsDescription {
  String loadBalancerName
  String vnet
  String subnet
  String subnetResourceId /* Azure resource ID */
  String vnetResourceGroup
  Boolean hasNewSubnet // used for tracking the subnets that are automatically created and are not selected by the user
  Boolean useDefaultVnet = false // default is for the user to provide us with an existing vnet and subnet
  String securityGroup
  String dnsName
  String cluster
  List<String> serverGroups
  String trafficEnabledSG
  String publicIpName
  List<AzureAppGatewayHealthcheckProbe> probes = []
  List<AzureAppGatewayRule> loadBalancingRules = []
  // TODO: remove hardcoded sku, tier and capacity
  //   We will come back and revisit this if we need to support different selection (we will also need deck changes to reflect that)
  String sku = "Standard_Small"
  String tier = "Standard"
  long capacity = 2

  static class AzureAppGatewayHealthcheckProbe {
    enum AzureLoadBalancerProbesType {
      HTTP
    }

    String probeName
    AzureLoadBalancerProbesType probeProtocol = AzureLoadBalancerProbesType.HTTP
    String probePort = "localhost"
    String probePath
    long probeInterval = 120
    long timeout = 30
    long unhealthyThreshold = 8
  }

  static class AzureAppGatewayRule {
    enum AzureLoadBalancingRulesType {
      HTTP
      // TODO: add support for HTTPS port mappings
    }

    String ruleName
    AzureLoadBalancingRulesType protocol
    long externalPort
    long backendPort
    // TODO: add support for HTTPS port mappings
    String sslCertificate
  }

  static AzureAppGatewayDescription getDescriptionForAppGateway(ApplicationGatewayInner appGateway) {
    AzureAppGatewayDescription description = new AzureAppGatewayDescription(name: appGateway.name())
    def parsedName = Names.parseName(appGateway.name())
    description.stack = appGateway.tags?.stack ?: parsedName.stack
    description.detail = appGateway.tags?.detail ?: parsedName.detail
    description.appName = appGateway.tags?.appName ?: parsedName.app
    description.loadBalancerName = appGateway.name()

    // Get current backend address pool id from the application gateway requested routing rules
    def bapActiveRuleId = appGateway.requestRoutingRules()?.first()?.backendAddressPool()?.id()
    if (bapActiveRuleId && AzureUtilities.getNameFromResourceId(bapActiveRuleId) != AzureAppGatewayResourceTemplate.defaultAppGatewayBeAddrPoolName) {
      description.trafficEnabledSG = AzureUtilities.getNameFromResourceId(bapActiveRuleId)
      description.cluster = Names.parseName(description.trafficEnabledSG).cluster
    } else {
      description.trafficEnabledSG = appGateway.tags?.trafficEnabledSG
      description.cluster = appGateway.tags?.cluster
    }

    // Each application gateway backend address pool corresponds to a server group (except the "defaul_BAP0")
    description.serverGroups = []
    appGateway.backendAddressPools()?.each { bap ->
      if (bap.name() != AzureAppGatewayResourceTemplate.defaultAppGatewayBeAddrPoolName) description.serverGroups << bap.name()
    }

    // We only support one subnet so we can just retrieve the first one
    description.subnetResourceId = appGateway?.gatewayIPConfigurations()?.first()?.subnet()?.id()
    description.subnet = AzureUtilities.getNameFromResourceId(description.subnetResourceId)
    description.vnet = AzureUtilities.getResourceNameFromId(description.subnetResourceId)
    description.vnetResourceGroup = AzureUtilities.getResourceGroupNameFromResourceId(description.subnetResourceId)
    description.hasNewSubnet = appGateway.tags?.hasNewSubnet

    description.publicIpName = AzureUtilities.getNameFromResourceId(appGateway?.frontendIPConfigurations().first().publicIPAddress().id())
    description.createdTime = appGateway.tags?.createdTime?.toLong()
    description.tags = appGateway.tags ?: [:]
    description.region = appGateway.location()

    appGateway.requestRoutingRules().each { rule ->
      def httpListener = appGateway.httpListeners().find { it.id() == rule.httpListener().id() }
      // Only HTTP protocol types are supported for now; ignore any other probes
      // TODO: add support for other protocols (if needed)
      if (httpListener && httpListener.protocol() == ApplicationGatewayProtocol.HTTP) {
        def frontendPort = appGateway.frontendPorts()?.find { it.id() == httpListener.frontendPort().id() }
        def backendHttpSettingsCollection = appGateway.backendHttpSettingsCollection()?.find { it.id() == rule.backendHttpSettings().id()}
        if (frontendPort && backendHttpSettingsCollection) {
          description.loadBalancingRules.add(
            new AzureAppGatewayRule(
              ruleName: rule.name(),
              externalPort: frontendPort.port(),
              backendPort: backendHttpSettingsCollection.port(),
              protocol: AzureAppGatewayRule.AzureLoadBalancingRulesType.HTTP
            ))
        }
      }
    }

    // Add the healthcheck probes
    appGateway.probes().each { probe ->
      // Only HTTP protocol types are supported for now; ignore any other probes
      // TODO: add support for other protocols (if needed)
      if (probe.protocol() == ApplicationGatewayProtocol.HTTP) {
        def p = new AzureAppGatewayHealthcheckProbe()
        p.probeName = probe.name()
        p.probePath = probe.path()
        p.probePort = probe.host()
        p.probeInterval = probe.interval()
        p.timeout = probe.timeout()
        p.unhealthyThreshold = probe.unhealthyThreshold()
        p.probeProtocol = AzureAppGatewayHealthcheckProbe.AzureLoadBalancerProbesType.HTTP
        description.probes.add(p)
      }
    }

    description
  }
}
