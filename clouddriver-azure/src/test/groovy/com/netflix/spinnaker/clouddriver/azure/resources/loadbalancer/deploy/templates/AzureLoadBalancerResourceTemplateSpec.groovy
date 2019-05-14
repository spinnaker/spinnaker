/*
 * Copyright 2015 The original authors.
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
package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.deploy.templates

import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureLoadBalancerResourceTemplate
import spock.lang.Specification

class AzureLoadBalancerResourceTemplateSpec extends Specification {
  AzureLoadBalancerDescription description

  void setup(){
    description = createDescription()
  }

  def 'should generate correct LoadBalancer create template'(){
    String template = AzureLoadBalancerResourceTemplate.getTemplate(description)

    expect: template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedFullTemplate
  }

  AzureLoadBalancerDescription createDescription(){
    AzureLoadBalancerDescription description = new AzureLoadBalancerDescription()
    description.cloudProvider = 'azure'
    description.appName = 'azuremasm'
    description.loadBalancerName = 'azuremasm-st1-d11'
    description.stack = 'st1'
    description.detail = 'd11'
    description.region = 'westus'
    description.vnet = null
    description.probes = new ArrayList<AzureLoadBalancerDescription.AzureLoadBalancerProbe>()

    AzureLoadBalancerDescription.AzureLoadBalancerProbe probe = new AzureLoadBalancerDescription.AzureLoadBalancerProbe()
    probe.probeName = 'healthcheck1'
    probe.probeProtocol = 'HTTP'
    probe.probePort = 7001
    probe.probePath = '/healthcheck'
    probe.probeInterval = 10
    probe.unhealthyThreshold = 2

    description.probes.add(probe)
    description.securityGroup = "azuremasm-sg1"
    description.vnet = "azuremasm-vnet-westus"
    description.subnet = "azuremasm-subnet-westus"
    description.loadBalancingRules = new ArrayList<AzureLoadBalancerDescription.AzureLoadBalancingRule>()

    AzureLoadBalancerDescription.AzureLoadBalancingRule rule = new AzureLoadBalancerDescription.AzureLoadBalancingRule()
    rule.ruleName = 'lbrule1'
    rule.protocol = 'TCP'
    rule.externalPort = 80
    rule.backendPort = 80
    rule.probeName = probe.probeName
    rule.persistence = 'None'
    rule.idleTimeout = 4

    description.loadBalancingRules.add(rule)
    description.inboundNATRules = new ArrayList<AzureLoadBalancerDescription.AzureLoadBalancerInboundNATRule>()

    AzureLoadBalancerDescription.AzureLoadBalancerInboundNATRule natRule = new AzureLoadBalancerDescription.AzureLoadBalancerInboundNATRule()
    natRule.ruleName = 'inboundRule1'
    natRule.serviceType = AzureLoadBalancerDescription.AzureLoadBalancerInboundNATRule.AzureLoadBalancerInboundNATRulesServiceType.SSH
    natRule.protocol = AzureLoadBalancerDescription.AzureLoadBalancerInboundNATRule.AzureLoadBalancerInboundNATRulesProtocolType.TCP
    natRule.port = 80

    description.inboundNATRules.add(natRule)
    description.name = 'azuremasm-st1-d11'
    description.user = '[anonymous]'

    return description
  }

  static String expectedFullTemplate = '''{
  "$schema" : "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#",
  "contentVersion" : "1.0.0.0",
  "parameters" : {
    "location" : {
      "type" : "string",
      "metadata" : {
        "description" : "Location to deploy"
      }
    }
  },
  "variables" : {
    "apiVersion" : "2018-08-01",
    "loadBalancerName" : "azuremasm-st1-d11",
    "virtualNetworkName" : "vnet-azuremasm-westus",
    "publicIPAddressName" : "pip-azuremasm-st1-d11",
    "loadBalancerFrontEnd" : "fe-azuremasm-st1-d11",
    "loadBalancerBackEnd" : "default_LB_BAP",
    "dnsNameForLBIP" : "[concat('dns-', uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11')))]",
    "ipConfigName" : "ipc-azuremasm-st1-d11",
    "loadBalancerID" : "[resourceID('Microsoft.Network/loadBalancers',variables('loadBalancerName'))]",
    "publicIPAddressID" : "[resourceID('Microsoft.Network/publicIPAddresses',variables('publicIPAddressName'))]",
    "frontEndIPConfig" : "[concat(variables('loadBalancerID'),'/frontendIPConfigurations/',variables('loadBalancerFrontEnd'))]",
    "backendPoolID" : "[concat(variables('loadBalancerID'),'/backendAddressPools/',variables('loadBalancerBackEnd'))]"
  },
  "resources" : [ {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('publicIPAddressName')]",
    "type" : "Microsoft.Network/publicIPAddresses",
    "location" : "[parameters('location')]",
    "tags" : null,
    "properties" : {
      "publicIPAllocationMethod" : "Static",
      "dnsSettings" : {
        "domainNameLabel" : "[variables('dnsNameForLBIP')]"
      }
    },
    "sku" : {
      "name" : "Standard"
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('loadBalancerName')]",
    "type" : "Microsoft.Network/loadBalancers",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azuremasm",
      "stack" : "st1",
      "detail" : "d11",
      "createdTime" : "1234567890"
    },
    "dependsOn" : [ "[concat('Microsoft.Network/publicIPAddresses/',variables('publicIPAddressName'))]" ],
    "properties" : {
      "frontEndIPConfigurations" : [ {
        "name" : "[variables('loadBalancerFrontEnd')]",
        "properties" : {
          "publicIPAddress" : {
            "id" : "[variables('publicIPAddressID')]"
          }
        }
      } ],
      "backendAddressPools" : [ {
        "name" : "[variables('loadBalancerBackEnd')]"
      } ],
      "loadBalancingRules" : [ {
        "name" : "lbrule1",
        "properties" : {
          "frontendIPConfiguration" : {
            "id" : "[variables('frontEndIPConfig')]"
          },
          "backendAddressPool" : {
            "id" : "[variables('backendPoolID')]"
          },
          "protocol" : "tcp",
          "frontendPort" : 80,
          "backendPort" : 80,
          "probe" : {
            "id" : "[concat(variables('loadBalancerID'),'/probes/healthcheck1')]"
          },
          "loadDistribution" : null
        }
      } ],
      "probes" : [ {
        "properties" : {
          "protocol" : "http",
          "port" : 7001,
          "intervalInSeconds" : 10,
          "requestPath" : "/healthcheck",
          "numberOfProbes" : 2
        },
        "name" : "healthcheck1"
      } ]
    },
    "sku" : {
      "name" : "Standard"
    }
  } ]
}'''
}
