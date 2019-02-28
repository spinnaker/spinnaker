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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.deploy.template

import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureAppGatewayResourceTemplate
import spock.lang.Specification


class AzureAppGatewayResourceTemplateSpec extends Specification {

  AzureAppGatewayDescription description

  void setup() {
    description = createDescription()
  }

  def 'generate an Azure Application Gateway resource template using a description object'() {
    String template = AzureAppGatewayResourceTemplate.getTemplate(description)

    expect: template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedFullTemplate
  }

  def 'generate an Azure Application Gateway resource template using a minimal description object'() {
    description = new AzureAppGatewayDescription()
    description.name = 'testappgw-lb1-d1'
    description.vnet = 'vnet-testappgw-westus'
    description.subnet = 'subnet-testappgw-lb1-d1'

    String template = AzureAppGatewayResourceTemplate.getTemplate(description)

    expect: template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedMinimalTemplate
  }

  def 'should fail to generate an Azure Application Gateway resource template using a description object with no name'() {
    when:
    description = new AzureAppGatewayDescription()

    AzureAppGatewayResourceTemplate.getTemplate(description)

    then:
    // The name is required and it must be set in the description object
    thrown(IllegalArgumentException)
  }

  def 'should fail to generate an Azure Application Gateway resource template using a description object with no vnet set'() {
    when:
    description = new AzureAppGatewayDescription()
    description.name = 'testappgw-lb1-d1'

    AzureAppGatewayResourceTemplate.getTemplate(description)

    then:
    // The vnet is required; it must be created prior to calling the getTemplate() and should be set in the description object
    thrown(IllegalArgumentException)
  }

  def 'should fail to generate an Azure Application Gateway resource template using a description object with no subnet set'() {
    when:
    description = new AzureAppGatewayDescription()
    description.name = 'testappgw-lb1-d1'
    description.vnet = 'vnet-testappgw-westus'

    AzureAppGatewayResourceTemplate.getTemplate(description)

    then:
    // The subnet is required; it must be created prior to calling the getTemplate() and should be set in the description object
    thrown(IllegalArgumentException)
  }

  private static AzureAppGatewayDescription createDescription() {
    AzureAppGatewayDescription description = new AzureAppGatewayDescription()
    description.name = 'testappgw-lb1-d1'
    description.cloudProvider = 'azure'
    description.appName = 'testappgw'
    description.stack = 'lb1'
    description.detail = 'd1'
    description.region = 'westus'
    description.user = '[anonymous]'
    description.loadBalancerName = description.name
    description.vnet = 'vnet-testappgw-westus'
    description.subnet = 'subnet-testappgw-lb1-d1'
    description.subnetResourceId = 'subnet-testappgw-lb1-d1'
    description.cluster = 'testappgw-sg1-d1'
    description.serverGroups = ['testappgw-sg1-d1-v000', 'testappgw-sg1-d1-v001']
    description.tags = ["key1":"val1", "key2":"val2"]

    description.probes.add(new AzureAppGatewayDescription.
      AzureAppGatewayHealthcheckProbe(
        probeName: 'probe1',
        probePath: '/healthcheck',
        probeInterval: 300,
        timeout: 60,
        unhealthyThreshold: 10
    ))

    description.loadBalancingRules.add(new AzureAppGatewayDescription.
      AzureAppGatewayRule(
        ruleName: 'rule1',
        protocol: 'HTTP',
        externalPort: 80,
        backendPort: 8080
    ))
    description.loadBalancingRules.add(new AzureAppGatewayDescription.
      AzureAppGatewayRule(
      ruleName: 'rule2',
      protocol: 'HTTP',
      externalPort: 8080,
      backendPort: 8080
    ))

    description
  }

  private static String expectedFullTemplate = '''{
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
    "apiVersion" : "2015-06-15",
    "appGwName" : "testappgw-lb1-d1",
    "publicIPAddressName" : "pip-testappgw-lb1-d1",
    "dnsNameForLBIP" : "[concat('dns-', uniqueString(concat(resourceGroup().id, subscription().id, 'testappgwlb1d1')))]",
    "appGwSubnetID" : "subnet-testappgw-lb1-d1",
    "publicIPAddressType" : "Dynamic",
    "publicIPAddressID" : "[resourceId('Microsoft.Network/publicIPAddresses',variables('publicIPAddressName'))]",
    "appGwID" : "[resourceId('Microsoft.Network/applicationGateways',variables('appGwName'))]",
    "appGwBeAddrPoolName" : "default_BAP0"
  },
  "resources" : [ {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('publicIPAddressName')]",
    "type" : "Microsoft.Network/publicIPAddresses",
    "location" : "[parameters('location')]",
    "tags" : null,
    "properties" : {
      "publicIPAllocationMethod" : "[variables('publicIPAddressType')]"
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('appGwName')]",
    "type" : "Microsoft.Network/applicationGateways",
    "location" : "[parameters('location')]",
    "tags" : {
      "key1" : "val1",
      "key2" : "val2",
      "createdTime" : "1234567890",
      "appName" : "testappgw",
      "stack" : "lb1",
      "detail" : "d1",
      "cluster" : "testappgw-sg1-d1",
      "serverGroups" : "testappgw-sg1-d1-v000 testappgw-sg1-d1-v001",
      "vnet" : "vnet-testappgw-westus",
      "subnet" : "subnet-testappgw-lb1-d1",
      "vnetResourceGroup" : null
    },
    "dependsOn" : [ "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]" ],
    "properties" : {
      "sku" : {
        "name" : "Standard_Small",
        "tier" : "Standard",
        "capacity" : "2"
      },
      "gatewayIPConfigurations" : [ {
        "name" : "appGwIpConfig",
        "properties" : {
          "subnet" : {
            "id" : "[variables('appGwSubnetID')]"
          }
        }
      } ],
      "frontendIPConfigurations" : [ {
        "name" : "appGwFrontendIP",
        "properties" : {
          "publicIPAddress" : {
            "id" : "[variables('publicIPAddressID')]"
          }
        }
      } ],
      "frontendPorts" : [ {
        "name" : "appGwFrontendPort-rule1",
        "properties" : {
          "port" : "80"
        }
      }, {
        "name" : "appGwFrontendPort-rule2",
        "properties" : {
          "port" : "8080"
        }
      } ],
      "backendAddressPools" : [ {
        "name" : "default_BAP0"
      }, {
        "name" : "testappgw-sg1-d1-v000"
      }, {
        "name" : "testappgw-sg1-d1-v001"
      } ],
      "backendHttpSettingsCollection" : [ {
        "name" : "appGwBackendHttpSettings-rule1",
        "properties" : {
          "port" : "8080",
          "protocol" : "HTTP",
          "cookieBasedAffinity" : "Disabled"
        }
      }, {
        "name" : "appGwBackendHttpSettings-rule2",
        "properties" : {
          "port" : "8080",
          "protocol" : "HTTP",
          "cookieBasedAffinity" : "Disabled"
        }
      } ],
      "httpListeners" : [ {
        "name" : "appGwHttpListener-rule1",
        "properties" : {
          "frontendIPConfiguration" : {
            "id" : "[concat(variables('appGwID'), '/frontendIPConfigurations/appGwFrontendIP')]"
          },
          "frontendPort" : {
            "id" : "[concat(variables('appGwID'), '/frontendPorts/appGwFrontendPort-rule1')]"
          },
          "protocol" : "HTTP",
          "sslCertificate" : null
        }
      }, {
        "name" : "appGwHttpListener-rule2",
        "properties" : {
          "frontendIPConfiguration" : {
            "id" : "[concat(variables('appGwID'), '/frontendIPConfigurations/appGwFrontendIP')]"
          },
          "frontendPort" : {
            "id" : "[concat(variables('appGwID'), '/frontendPorts/appGwFrontendPort-rule2')]"
          },
          "protocol" : "HTTP",
          "sslCertificate" : null
        }
      } ],
      "requestRoutingRules" : [ {
        "name" : "rule1",
        "properties" : {
          "ruleType" : "Basic",
          "httpListener" : {
            "id" : "[concat(variables('appGwID'), '/httpListeners/appGwHttpListener-rule1')]"
          },
          "backendAddressPool" : {
            "id" : "[concat(variables('appGwID'), '/backendAddressPools/', variables('appGwBeAddrPoolName'))]"
          },
          "backendHttpSettings" : {
            "id" : "[concat(variables('appGwID'), '/backendHttpSettingsCollection/appGwBackendHttpSettings-rule1')]"
          }
        }
      }, {
        "name" : "rule2",
        "properties" : {
          "ruleType" : "Basic",
          "httpListener" : {
            "id" : "[concat(variables('appGwID'), '/httpListeners/appGwHttpListener-rule2')]"
          },
          "backendAddressPool" : {
            "id" : "[concat(variables('appGwID'), '/backendAddressPools/', variables('appGwBeAddrPoolName'))]"
          },
          "backendHttpSettings" : {
            "id" : "[concat(variables('appGwID'), '/backendHttpSettingsCollection/appGwBackendHttpSettings-rule2')]"
          }
        }
      } ],
      "probes" : [ {
        "name" : "probe1",
        "properties" : {
          "protocol" : "HTTP",
          "host" : "localhost",
          "path" : "/healthcheck",
          "interval" : 300,
          "timeout" : 60,
          "unhealthyThreshold" : 10
        }
      } ]
    }
  } ]
}'''

  private static String expectedMinimalTemplate = '''{
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
    "apiVersion" : "2015-06-15",
    "appGwName" : "testappgw-lb1-d1",
    "publicIPAddressName" : "pip-testappgw-lb1-d1",
    "dnsNameForLBIP" : "[concat('dns-', uniqueString(concat(resourceGroup().id, subscription().id, 'testappgwlb1d1')))]",
    "appGwSubnetID" : null,
    "publicIPAddressType" : "Dynamic",
    "publicIPAddressID" : "[resourceId('Microsoft.Network/publicIPAddresses',variables('publicIPAddressName'))]",
    "appGwID" : "[resourceId('Microsoft.Network/applicationGateways',variables('appGwName'))]",
    "appGwBeAddrPoolName" : "default_BAP0"
  },
  "resources" : [ {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('publicIPAddressName')]",
    "type" : "Microsoft.Network/publicIPAddresses",
    "location" : "[parameters('location')]",
    "tags" : null,
    "properties" : {
      "publicIPAllocationMethod" : "[variables('publicIPAddressType')]"
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('appGwName')]",
    "type" : "Microsoft.Network/applicationGateways",
    "location" : "[parameters('location')]",
    "tags" : {
      "createdTime" : "1234567890",
      "vnet" : "vnet-testappgw-westus",
      "subnet" : "subnet-testappgw-lb1-d1",
      "vnetResourceGroup" : null
    },
    "dependsOn" : [ "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]" ],
    "properties" : {
      "sku" : {
        "name" : "Standard_Small",
        "tier" : "Standard",
        "capacity" : "2"
      },
      "gatewayIPConfigurations" : [ {
        "name" : "appGwIpConfig",
        "properties" : {
          "subnet" : {
            "id" : "[variables('appGwSubnetID')]"
          }
        }
      } ],
      "frontendIPConfigurations" : [ {
        "name" : "appGwFrontendIP",
        "properties" : {
          "publicIPAddress" : {
            "id" : "[variables('publicIPAddressID')]"
          }
        }
      } ],
      "frontendPorts" : [ ],
      "backendAddressPools" : [ {
        "name" : "default_BAP0"
      } ],
      "backendHttpSettingsCollection" : [ ],
      "httpListeners" : [ ],
      "requestRoutingRules" : [ ],
      "probes" : [ ]
    }
  } ]
}'''

}
