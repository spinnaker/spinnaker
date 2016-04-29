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

package com.netflix.spinnaker.clouddriver.azure.templates

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription

@Slf4j
class AzureAppGatewayResourceTemplate {

  static ObjectMapper mapper = new ObjectMapper()
    .configure(SerializationFeature.INDENT_OUTPUT, true)
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

  /**
   * Build the resource manager template that will create an Azure Application Gateway resource
   * @param description - Description object containing the values to be specified in the template
   * @return - JSON string representing the Resource Manager template for an Azure Application Gateway
   */
  static String getTemplate(AzureAppGatewayDescription description) {
    if (!description || !description.name || !description.vnet || !description.subnet) {
      throw new IllegalArgumentException("Invalid description object: name, vnet and subnet can't be empty")
    }

    AppGatewayTemplate template = new AppGatewayTemplate(description)
    mapper.writeValueAsString(template)
  }

  static class AppGatewayTemplate {
    String $schema = "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#"
    String contentVersion = "1.0.0.0"

    AppGatewayTemplateParameters parameters
    AppGatewayTemplateVariables variables
    ArrayList<Resource> resources = []

    /**
     *
     * @param description - Description object containing the values to be specified in the template
     */
    AppGatewayTemplate(AzureAppGatewayDescription description) {
      parameters = new AppGatewayTemplateParameters()
      variables = new AppGatewayTemplateVariables(description)
      resources.add(new PublicIpResource())
      resources.add(new ApplicationGatewayResource(description))
    }
  }

  static class AppGatewayTemplateParameters {
    LocationParameter location = new LocationParameter()
  }

  static class LocationParameter {
    String type = "string"
    Map<String, String> metadata = ["description":"Location to deploy"]
  }

  static class AppGatewayTemplateVariables {
    final String apiVersion = "2015-06-15"
    String appGwName
    String virtualNetworkName
    String publicIPAddressName
    String dnsNameForLBIP
    String appGwSubnetName

    final String virtualNetworkID = "[resourceId('Microsoft.Network/virtualNetworks',variables('virtualNetworkName'))]"
    final String publicIPAddressType = "Dynamic"
    final String publicIPAddressID = "[resourceId('Microsoft.Network/publicIPAddresses',variables('publicIPAddressName'))]"
    final String appGwID = "[resourceId('Microsoft.Network/applicationGateways',variables('appGwName'))]"
    final String appGwSubnetID = "[concat(variables('virtualNetworkID'),'/subnets/',variables('appGwSubnetName'))]"
    final String appGwBeAddrPoolName = "beaddrpool-default"

    AppGatewayTemplateVariables(AzureAppGatewayDescription description) {
      appGwName = description.name
      virtualNetworkName = description.vnet.toLowerCase()
      publicIPAddressName = AzureUtilities.PUBLICIP_NAME_PREFIX + description.name.toLowerCase()
      dnsNameForLBIP = DnsSettings.getUniqueDNSName(description.name)
      appGwSubnetName = description.subnet.toLowerCase()
    }
  }

  static class ApplicationGatewayResource extends DependingResource {
    ApplicationGatewayResourceProperties properties

    ApplicationGatewayResource(AzureAppGatewayDescription description) {
      def currentTime = System.currentTimeMillis()
      apiVersion = "[variables('apiVersion')]"
      name = "[variables('appGwName')]"
      type = "Microsoft.Network/applicationGateways"
      location = "[parameters('location')]"
      tags = [:]
      description.tags?.each { key, value ->
        tags[key] = value
      }
      tags.createdTime = currentTime.toString()
      if (description.appName) tags.appName = description.appName
      if (description.stack) tags.stack = description.stack
      if (description.detail) tags.detail = description.detail
      if (description.cluster) tags.cluster = description.cluster
      if (description.serverGroups) tags.serverGroups = description.serverGroups.join(" ")
      if (description.securityGroup) tags.securityGroup = description.securityGroup
      if (description.vnet) tags.vnet = description.vnet
      if (description.subnet) tags.subnet = description.subnet
      this.dependsOn.add("[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]")
      properties = new ApplicationGatewayResourceProperties(description)
    }
  }

  static class ApplicationGatewayResourceProperties {
    AppGatewaySku sku
    List<AppGatewayIPConfiguration> gatewayIPConfigurations
    List<AppGatewayFrontendIPConfiguration> frontendIPConfigurations
    List<AppGatewayFrontendPort> frontendPorts = []
    List<AppGatewayBackendAddressPool> backendAddressPools = []
    List<AppGatewayBackendHttpSettingsCollection> backendHttpSettingsCollection = []
    List<AppGatewayHttpListener> httpListeners = []
    List<AppGatewayRequestRoutingRule> requestRoutingRules = []
    List<AppGatewayProbe> probes = []

    ApplicationGatewayResourceProperties(AzureAppGatewayDescription description) {
      sku = new AppGatewaySku(description)
      gatewayIPConfigurations = [ new AppGatewayIPConfiguration()]
      frontendIPConfigurations = [new AppGatewayFrontendIPConfiguration()]
      description.rules?.each { rule ->
        frontendPorts.add(new AppGatewayFrontendPort(rule.name, rule.externalPort))
        backendHttpSettingsCollection.add(new AppGatewayBackendHttpSettingsCollection(rule.name, rule.protocol.toString(), rule.backendPort))
        httpListeners.add(new AppGatewayHttpListener(rule.name, rule.protocol.toString(), rule.sslCertificate))
        requestRoutingRules.add(new AppGatewayRequestRoutingRule(rule.name))
      }
      backendAddressPools = [new AppGatewayBackendAddressPool()]
      description.probes?.each { probe->
        probes.add(new AppGatewayProbe(probe))
      }
    }
  }

  static class AppGatewaySku {
    String name
    String tier
    String capacity

    AppGatewaySku(AzureAppGatewayDescription description) {
      name = description.sku
      tier = description.tier
      capacity = description.capacity
    }
  }

  static class AppGatewayIPConfiguration {
    final String name = "appGwIpConfig"
    final AppGatewayIPConfigurationProperties properties = new AppGatewayIPConfigurationProperties()

    static class AppGatewayIPConfigurationProperties {
      final Map<String, String> subnet = [ "id" : "[variables('appGwSubnetID')]" ]
    }
  }

  static class AppGatewayFrontendIPConfiguration {
    final String name = "appGwFrontendIP"
    final AppGatewayFrontendIPConfigurationProperties properties = new AppGatewayFrontendIPConfigurationProperties()

    static class AppGatewayFrontendIPConfigurationProperties {
      final Map<String, String> publicIPAddress = ["id" : "[variables('publicIPAddressID')]"]
    }
  }

  static class AppGatewayFrontendPort {
    String name
    Map<String, String> properties = [:]

    AppGatewayFrontendPort(String ruleName, long rulePort) {
      name = "appGwFrontendPort-" + ruleName
      properties.port = rulePort.toString()
    }
  }

  static class AppGatewayBackendAddressPool {
    final String name = "[variables('appGwBeAddrPoolName')]"
  }

  static class AppGatewayBackendHttpSettingsCollection {
    String name
    Map<String, String> properties = [:]

    AppGatewayBackendHttpSettingsCollection(String ruleName, String ruleProtocol, long rulePort) {
      name = "appGwBackendHttpSettings-" + ruleName
      properties.port = rulePort.toString()
      properties.protocol = ruleProtocol
      properties.cookieBasedAffinity = "Disabled"
    }
  }

  static class AppGatewayHttpListener {
    String name
    AppGatewayHttpListenerProperties properties

    AppGatewayHttpListener(String ruleName, String ruleProtocol, String ruleSslCertificate) {
      name = "appGwHttpListener-" + ruleName
      properties = new AppGatewayHttpListenerProperties(ruleName, ruleProtocol, ruleSslCertificate)
    }

    static class AppGatewayHttpListenerProperties {
      Map<String, String> frontendIPConfiguration = [:]
      Map<String, String> frontendPort = [:]
      String protocol
      String sslCertificate

      AppGatewayHttpListenerProperties(String ruleName, String ruleProtocol, String ruleSslCertificate) {
        frontendIPConfiguration.id = "[concat(variables('appGwID'), '/frontendIPConfigurations/appGwFrontendIP')]"
        frontendPort.id = "[concat(variables('appGwID'), '/frontendPorts/appGwFrontendPort-" + ruleName + "')]"
        protocol = ruleProtocol
        sslCertificate = ruleSslCertificate
      }
    }
  }

  static class AppGatewayRequestRoutingRule {
    String name
    AppGatewayRequestRoutingRuleProperties properties

    AppGatewayRequestRoutingRule(String ruleName) {
      name = ruleName
      properties = new AppGatewayRequestRoutingRuleProperties(ruleName)
    }

    static class AppGatewayRequestRoutingRuleProperties {
      final String ruleType = "Basic"
      Map<String, String> httpListener = [:]
      Map<String, String> backendAddressPool = [:]
      Map<String, String> backendHttpSettings = [:]

      AppGatewayRequestRoutingRuleProperties(String ruleName) {
        httpListener.id = "[concat(variables('appGwID'), '/httpListeners/appGwHttpListener-" + ruleName + "')]"
        backendAddressPool.id = "[concat(variables('appGwID'), '/backendAddressPools/', variables('appGwBeAddrPoolName'))]"
        backendHttpSettings.id = "[concat(variables('appGwID'), '/backendHttpSettingsCollection/appGwBackendHttpSettings-" + ruleName + "')]"
      }
    }
  }

  static class AppGatewayProbe {
    String name
    AppGatewayProbeProperties properties

    AppGatewayProbe(AzureAppGatewayDescription.AzureAppGatewayHealthcheckProbe probe) {
      name = probe.name
      properties = new AppGatewayProbeProperties(
        protocol: probe.protocol.toString(),
        host: probe.host,
        path: probe.path,
        interval: probe.interval,
        timeout: probe.timeout,
        unhealthyThreshold: probe.unhealthyThreshold
      )
    }

    static class AppGatewayProbeProperties{
      String protocol
      String host
      String path
      long interval
      long timeout
      long unhealthyThreshold
    }
  }
}
