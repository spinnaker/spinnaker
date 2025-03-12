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
package com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.deploy.templates

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureSecurityGroupResourceTemplate
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.UpsertAzureSecurityGroupDescription
import spock.lang.Shared
import spock.lang.Specification

class AzureSecurityGroupResourceTemplateSpec extends Specification {
  UpsertAzureSecurityGroupDescription description

  void setup(){
    description = createDescription()
  }

  def 'should generate a correct Azure Security Group create template'(){
    String template = AzureSecurityGroupResourceTemplate.getTemplate(description)

    expect: template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedFullTemplate
  }

  UpsertAzureSecurityGroupDescription createNoRulesDescription(){
    new UpsertAzureSecurityGroupDescription()
  }

  UpsertAzureSecurityGroupDescription createDescription(){
    UpsertAzureSecurityGroupDescription description = new UpsertAzureSecurityGroupDescription()
    description.cloudProvider = 'azure'
    description.appName = 'azureMASM'
    description.securityGroupName = 'azureMASM-sg1-d11'
    description.detail = 'd11'
    description.region = 'westus'
    description.name = 'azureMASM-sg1-d11'
    description.user = '[anonymous]'

    AzureSecurityGroupDescription.AzureSGRule securityRule1 = new AzureSecurityGroupDescription.AzureSGRule(
      name: 'rule1',
      description: 'Allow FE Subnet',
      protocol: 'TCP',
      sourcePortRange: '*',
      destinationPortRange: '433',
      sourceAddressPrefix: '10.0.0.0/24',
      destinationAddressPrefix: '*',
      access: 'Allow',
      priority: 100,
      direction: 'Inbound'
    )
    description.securityRules.add(securityRule1)
    AzureSecurityGroupDescription.AzureSGRule securityRule2 = new AzureSecurityGroupDescription.AzureSGRule(
      name: 'rule2',
      description: 'Block RDP',
      protocol: 'TCP',
      sourcePortRange: '*',
      destinationPortRange: '3389',
      sourceAddressPrefix: 'Internet',
      destinationAddressPrefix: '*',
      access: 'Deny',
      priority: 101,
      direction: 'Inbound'
    )
    description.securityRules.add(securityRule2)

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
    },
    "networkSecurityGroupName" : {
      "type" : "string",
      "metadata" : {
        "description" : "The NSG name"
      }
    },
    "networkSecurityGroupResourceGroupName" : {
      "type" : "string",
      "metadata" : {
        "description" : "The resource group name of NSG"
      }
    },
    "virtualNetworkName" : {
      "type" : "string",
      "defaultValue" : "",
      "metadata" : {
        "description" : "The Virtual Network name"
      }
    },
    "virtualNetworkResourceGroupName" : {
      "type" : "string",
      "defaultValue" : "",
      "metadata" : {
        "description" : "The resource group name of Virtual Network"
      }
    },
    "subnetName" : {
      "type" : "string",
      "defaultValue" : "",
      "metadata" : {
        "description" : "The subnet name"
      }
    }
  },
  "variables" : {
    "securityGroupName" : "azuremasm-sg1-d11"
  },
  "resources" : [ {
    "apiVersion" : "2018-11-01",
    "name" : "[variables('securityGroupName')]",
    "type" : "Microsoft.Network/networkSecurityGroups",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "none",
      "detail" : "d11",
      "createdTime" : "1234567890"
    },
    "dependsOn" : [ ],
    "properties" : {
      "securityRules" : [ {
        "name" : "rule1",
        "properties" : {
          "description" : "Allow FE Subnet",
          "access" : "Allow",
          "destinationAddressPrefix" : "*",
          "destinationPortRange" : "433",
          "destinationPortRanges" : null,
          "direction" : "Inbound",
          "priority" : 100,
          "protocol" : "TCP",
          "sourceAddressPrefix" : "10.0.0.0/24",
          "sourceAddressPrefixes" : null,
          "sourcePortRange" : "*"
        }
      }, {
        "name" : "rule2",
        "properties" : {
          "description" : "Block RDP",
          "access" : "Deny",
          "destinationAddressPrefix" : "*",
          "destinationPortRange" : "3389",
          "destinationPortRanges" : null,
          "direction" : "Inbound",
          "priority" : 101,
          "protocol" : "TCP",
          "sourceAddressPrefix" : "Internet",
          "sourceAddressPrefixes" : null,
          "sourcePortRange" : "*"
        }
      } ]
    }
  } ]
}'''
}
