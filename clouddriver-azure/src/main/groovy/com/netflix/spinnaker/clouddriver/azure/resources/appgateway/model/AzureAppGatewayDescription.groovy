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

import com.microsoft.azure.management.network.models.ApplicationGateway
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class AzureAppGatewayDescription extends AzureResourceOpsDescription {
  String loadBalancerName
  String vnet
  String subnet
  String securityGroup
  String dnsName
  String cluster
  List<String> serverGroups
  List<AzureAppGatewayHealthcheckProbe> probes = []
  List<AzureAppGatewayRule> rules = []
  // TODO: remove hardcoded sku, tier and capacity
  //   We will come back and revisit this if we need to support different selection (we will also need deck changes to reflect that)
  String sku = "Standard_Small"
  String tier = "Standard"
  long capacity = 2

  static class AzureAppGatewayHealthcheckProbe {
    enum AzureLoadBalancerProbesType {
      HTTP
    }

    String name
    AzureLoadBalancerProbesType protocol = AzureLoadBalancerProbesType.HTTP
    String host = "localhost"
    String path
    long interval = 120
    long timeout = 30
    long unhealthyThreshold = 8
  }

  static class AzureAppGatewayRule {
    enum AzureLoadBalancingRulesType {
      HTTP
      // TODO: add support for HTTPS port mappings
    }

    String name
    AzureLoadBalancingRulesType protocol
    long externalPort
    long backendPort
    // TODO: add support for HTTPS port mappings
    String sslCertificate
  }

  static AzureAppGatewayDescription getDescriptionForAppGateway(ApplicationGateway appGateway) {
    AzureAppGatewayDescription description = new AzureAppGatewayDescription(name: appGateway.name)
    def parsedName = Names.parseName(appGateway.name)
    description.stack = appGateway.tags?.stack ?: parsedName.stack
    description.detail = appGateway.tags?.detail ?: parsedName.detail
    description.appName = appGateway.tags?.appName ?: parsedName.app
    description.loadBalancerName = appGateway.name
    description.cluster = appGateway.tags?.cluster
    description.serverGroups = appGateway.tags?.serverGroups?.split(" ")
    description.vnet = appGateway.tags?.vnet
    description.createdTime = appGateway.tags?.createdTime?.toLong()
    description.tags = appGateway.tags
    description.region = appGateway.location

    appGateway.requestRoutingRules.each { rule ->
      def httpListener = appGateway.httpListeners.find { it.id == rule.httpListener.id }
      // Only HTTP protocol types are supported for now; ignore any other probes
      // TODO: add support for other protocols (if needed)
      if (httpListener && httpListener.protocol.toUpperCase() == "HTTP") {
        def frontendPort = appGateway.frontendPorts?.find { it.id == httpListener.frontendPort.id }
        def backendHttpSettingsCollection = appGateway.backendHttpSettingsCollection?.find { it.id == rule.backendHttpSettings.id}
        if (frontendPort && backendHttpSettingsCollection) {
          description.rules.add(
            new AzureAppGatewayRule(
              name: rule.name,
              externalPort: frontendPort.port,
              backendPort: backendHttpSettingsCollection.port
            ))
        }
      }
    }

    // Add the healthcheck probes
    appGateway.probes.each { probe ->
      // Only HTTP protocol types are supported for now; ignore any other probes
      // TODO: add support for other protocols (if needed)
      if (probe.protocol.toUpperCase() == "HTTP") {
        def p = new AzureAppGatewayHealthcheckProbe()
        p.name = probe.name
        p.path = probe.path
        p.host = probe.host
        p.interval = probe.interval
        p.timeout = probe.timeout
        p.unhealthyThreshold = probe.unhealthyThreshold
        p.protocol = AzureAppGatewayHealthcheckProbe.AzureLoadBalancerProbesType.HTTP
        description.probes.add(p)
      }
    }

    description
  }
}
