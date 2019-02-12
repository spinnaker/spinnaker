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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroups.deploy.templates

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.KeyVaultSecret
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage
import com.netflix.spinnaker.clouddriver.azure.templates.AzureServerGroupResourceTemplate
import spock.lang.Specification

class AzureServerGroupResourceTemplateSpec extends Specification {
  ObjectMapper objectMapper
  AzureServerGroupDescription description

  void setup() {
    description = createDescription(false)
    objectMapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  def 'should generate correct ServerGroup resource template'() {
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"') == expectedFullTemplate
  }

  def 'should generate correct ServerGroup resource template with custom image'() {
    description = createDescription(true)
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"') == expectedFullTemplateWithCustomImage
  }

  def 'generate server group template with extensions profile for linux'() {
    description = createCustomDescription()
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"') == expectedCustomScriptTemplateLinux
  }

  def 'generate server group template with extension profile for windows'() {
    description = createCustomDescription(true)
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"') == expectedCustomScriptTemplateWindows
  }

  def 'generate server group template with custom data'() {
    description = createCustomDescription()

    description.osConfig.customData = "this is test custom data"

    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"') == expectedCustomDataTemplate
  }

  def 'verify parameters JSON'() {

    def parameters = [:]
    parameters[AzureServerGroupResourceTemplate.subnetParameterName] = subnetId
    parameters[AzureServerGroupResourceTemplate.vmPasswordParameterName] = new KeyVaultSecret(secretName, subscriptionId, defaultResourceGroup, defaultVaultName)
    String parametersJSON = AzureResourceManagerClient.convertParametersToTemplateJSON(objectMapper, parameters)

    expect: parametersJSON == expectedParameters
  }

  private static AzureServerGroupDescription createDescription(boolean withCustomImage = false) {
    AzureServerGroupDescription description = new AzureServerGroupDescription()
    description.name = 'azureMASM-st1-d11'
    description.cloudProvider = 'azure'
    description.application = 'azureMASM'
    description.stack = 'st1'
    description.detail = 'd11'
    description.clusterName = description.getClusterName()
    description.region = 'westus'
    description.user = '[anonymous]'

    description.upgradePolicy = AzureServerGroupDescription.UpgradePolicy.Manual

    AzureNamedImage image = new AzureNamedImage()
    if (withCustomImage) {
      image.isCustom = true
      image.ostype = 'Linux'
      image.region = 'westus'
      image.uri = '/subscriptions/faab228d-df7a-4086-991e-e81c4659d41a/resourceGroups/zhqqi-sntest/providers/Microsoft.Compute/images/hello-karyon-rxnetty-all-20190125054410-ubuntu-1604'
    } else {
      image.sku = '14.04.3-LTS'
      image.offer = 'UbuntuServer'
      image.publisher = 'Canonical'
      image.version = 'latest'
    }
    description.image = image

    AzureServerGroupDescription.AzureScaleSetSku scaleSetSku = new AzureServerGroupDescription.AzureScaleSetSku()
    scaleSetSku.name = 'Standard_A1'
    scaleSetSku.capacity = 2
    scaleSetSku.tier = 'Standard'
    description.sku = scaleSetSku

    AzureServerGroupDescription.AzureOperatingSystemConfig config = new AzureServerGroupDescription.AzureOperatingSystemConfig()
    description.osConfig = config

    int backendPort = withCustomImage ? 22 : 3389
    description.addInboundPortConfig("InboundPortConfig", 50000, 50099, "tcp", backendPort)

    description
  }

  private static AzureServerGroupDescription createCustomDescription(boolean targetWindows = false) {
    AzureServerGroupDescription description = createDescription()
    AzureServerGroupDescription.AzureExtensionCustomScriptSettings extension = new AzureServerGroupDescription.AzureExtensionCustomScriptSettings()
    extension.commandToExecute = "mkdir mydir"
    extension.fileUris = ["storage1", "file2"]
    description.customScriptsSettings = extension

    //Set the OS type and backend port accordingly
    description.image.ostype = targetWindows ? "Windows" : "Linux"
    description.inboundPortConfigs[0].backendPort = targetWindows ? 3389 : 22

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
    },
    "subnetId" : {
      "type" : "string",
      "metadata" : {
        "description" : "Subnet Resource ID"
      }
    },
    "appGatewayAddressPoolId" : {
      "type" : "string",
      "metadata" : {
        "description" : "App Gateway backend address pool resource ID"
      }
    },
    "vmuserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account name"
      }
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account password"
      }
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : ""
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
    "publicIPAddressName" : "pip-azureMASM-st1-d11",
    "publicIPAddressID" : "[resourceId('Microsoft.Network/publicIPAddresses', variables('publicIPAddressName'))]",
    "publicIPAddressType" : "Dynamic",
    "dnsNameForLBIP" : "dns-azuremasm-st1-d11",
    "loadBalancerBackend" : "be-azureMASM-st1-d11",
    "loadBalancerFrontEnd" : "fe-azureMASM-st1-d11",
    "loadBalancerName" : "lb-azureMASM-st1-d11",
    "loadBalancerID" : "[resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName'))]",
    "frontEndIPConfigID" : "[resourceId('Microsoft.Network/loadBalancers/frontendIPConfigurations/', variables('loadBalancerName'), variables('loadBalancerFrontEnd'))]",
    "inboundNatPoolName" : "np-azureMASM-st1-d11",
    "vhdContainerName" : "azuremasm-st1-d11",
    "osType" : {
      "publisher" : "Canonical",
      "offer" : "UbuntuServer",
      "sku" : "14.04.3-LTS",
      "version" : "latest"
    },
    "imageReference" : "[variables('osType')]",
    "uniqueStorageNameArray" : [ "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]" ]
  },
  "resources" : [ {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[concat(variables('uniqueStorageNameArray')[copyIndex()])]",
    "type" : "Microsoft.Storage/storageAccounts",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "serverGroupName" : "azureMASM-st1-d11",
      "createdTime" : "1234567890"
    },
    "copy" : {
      "name" : "storageLoop",
      "count" : 1
    },
    "properties" : {
      "accountType" : "Premium_LRS"
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('publicIPAddressName')]",
    "type" : "Microsoft.Network/publicIPAddresses",
    "location" : "[parameters('location')]",
    "tags" : null,
    "properties" : {
      "publicIPAllocationMethod" : "[variables('publicIPAddressType')]",
      "dnsSettings" : {
        "domainNameLabel" : "[variables('dnsNameForLBIP')]"
      }
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('loadBalancerName')]",
    "type" : "Microsoft.Network/loadBalancers",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "createdTime" : "1234567890",
      "cluster" : "azureMASM-st1-d11",
      "serverGroup" : "azureMASM-st1-d11"
    },
    "dependsOn" : [ "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]" ],
    "properties" : {
      "frontendIPConfigurations" : [ {
        "name" : "[variables('loadBalancerFrontEnd')]",
        "properties" : {
          "publicIpAddress" : {
            "id" : "[variables('publicIPAddressID')]"
          }
        }
      } ],
      "backendAddressPools" : [ {
        "name" : "[variables('loadBalancerBackEnd')]"
      } ],
      "inboundNatPools" : [ {
        "name" : "InboundPortConfig",
        "properties" : {
          "frontendIPConfiguration" : {
            "id" : "[variables('frontEndIPConfigID')]"
          },
          "protocol" : "tcp",
          "frontendPortRangeStart" : 50000,
          "frontendPortRangeEnd" : 50099,
          "backendPort" : 3389
        }
      } ]
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "loadBalancerName" : "lb-azureMASM-st1-d11",
      "hasNewSubnet" : "false",
      "imageIsCustom" : "false",
      "storageAccountNames" : "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]"
    },
    "dependsOn" : [ "[concat('Microsoft.Storage/storageAccounts/', variables('uniqueStorageNameArray')[0])]", "[concat('Microsoft.Network/loadBalancers/', variables('loadBalancerName'))]" ],
    "sku" : {
      "name" : "Standard_A1",
      "tier" : "Standard",
      "capacity" : 2
    },
    "properties" : {
      "upgradePolicy" : {
        "mode" : "Manual"
      },
      "virtualMachineProfile" : {
        "storageProfile" : {
          "osDisk" : {
            "name" : "osdisk-azureMASM-st1-d11",
            "caching" : "ReadOnly",
            "createOption" : "FromImage",
            "vhdContainers" : [ "[concat('https://', variables('uniqueStorageNameArray')[0], '.blob.core.windows.net/', variables('vhdContainerName'))]" ]
          },
          "imageReference" : "[variables('imageReference')]"
        },
        "osProfile" : {
          "computerNamePrefix" : "azureMASM-",
          "adminUsername" : "[parameters('vmUsername')]",
          "adminPassword" : "[parameters('vmPassword')]"
        },
        "networkProfile" : {
          "networkInterfaceConfigurations" : [ {
            "name" : "nic-azureMASM-st1-d11",
            "properties" : {
              "primary" : true,
              "ipConfigurations" : [ {
                "name" : "ipc-azureMASM-st1-d11",
                "properties" : {
                  "subnet" : {
                    "id" : "[parameters('subnetId')]"
                  },
                  "loadBalancerBackendAddressPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', variables('loadBalancerName'), variables('loadBalancerBackend'))]"
                  } ],
                  "loadBalancerInboundNatPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/inboundNatPools', variables('loadBalancerName'), variables('inboundNatPoolName'))]"
                  } ],
                  "applicationGatewayBackendAddressPools" : [ {
                    "id" : "[parameters('appGatewayAddressPoolId')]"
                  } ]
                }
              } ]
            }
          } ]
        }
      }
    }
  } ]
}'''

  private static String expectedFullTemplateWithCustomImage = '''{
  "$schema" : "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#",
  "contentVersion" : "1.0.0.0",
  "parameters" : {
    "location" : {
      "type" : "string",
      "metadata" : {
        "description" : "Location to deploy"
      }
    },
    "subnetId" : {
      "type" : "string",
      "metadata" : {
        "description" : "Subnet Resource ID"
      }
    },
    "appGatewayAddressPoolId" : {
      "type" : "string",
      "metadata" : {
        "description" : "App Gateway backend address pool resource ID"
      }
    },
    "vmuserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account name"
      }
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account password"
      }
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : ""
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
    "publicIPAddressName" : "pip-azureMASM-st1-d11",
    "publicIPAddressID" : "[resourceId('Microsoft.Network/publicIPAddresses', variables('publicIPAddressName'))]",
    "publicIPAddressType" : "Dynamic",
    "dnsNameForLBIP" : "dns-azuremasm-st1-d11",
    "loadBalancerBackend" : "be-azureMASM-st1-d11",
    "loadBalancerFrontEnd" : "fe-azureMASM-st1-d11",
    "loadBalancerName" : "lb-azureMASM-st1-d11",
    "loadBalancerID" : "[resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName'))]",
    "frontEndIPConfigID" : "[resourceId('Microsoft.Network/loadBalancers/frontendIPConfigurations/', variables('loadBalancerName'), variables('loadBalancerFrontEnd'))]",
    "inboundNatPoolName" : "np-azureMASM-st1-d11"
  },
  "resources" : [ {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('publicIPAddressName')]",
    "type" : "Microsoft.Network/publicIPAddresses",
    "location" : "[parameters('location')]",
    "tags" : null,
    "properties" : {
      "publicIPAllocationMethod" : "[variables('publicIPAddressType')]",
      "dnsSettings" : {
        "domainNameLabel" : "[variables('dnsNameForLBIP')]"
      }
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('loadBalancerName')]",
    "type" : "Microsoft.Network/loadBalancers",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "createdTime" : "1234567890",
      "cluster" : "azureMASM-st1-d11",
      "serverGroup" : "azureMASM-st1-d11"
    },
    "dependsOn" : [ "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]" ],
    "properties" : {
      "frontendIPConfigurations" : [ {
        "name" : "[variables('loadBalancerFrontEnd')]",
        "properties" : {
          "publicIpAddress" : {
            "id" : "[variables('publicIPAddressID')]"
          }
        }
      } ],
      "backendAddressPools" : [ {
        "name" : "[variables('loadBalancerBackEnd')]"
      } ],
      "inboundNatPools" : [ {
        "name" : "InboundPortConfig",
        "properties" : {
          "frontendIPConfiguration" : {
            "id" : "[variables('frontEndIPConfigID')]"
          },
          "protocol" : "tcp",
          "frontendPortRangeStart" : 50000,
          "frontendPortRangeEnd" : 50099,
          "backendPort" : 22
        }
      } ]
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "loadBalancerName" : "lb-azureMASM-st1-d11",
      "hasNewSubnet" : "false",
      "imageIsCustom" : "true"
    },
    "dependsOn" : [ "[concat('Microsoft.Network/loadBalancers/', variables('loadBalancerName'))]" ],
    "sku" : {
      "name" : "Standard_A1",
      "tier" : "Standard",
      "capacity" : 2
    },
    "properties" : {
      "upgradePolicy" : {
        "mode" : "Manual"
      },
      "virtualMachineProfile" : {
        "storageProfile" : {
          "imageReference" : {
            "id" : "/subscriptions/faab228d-df7a-4086-991e-e81c4659d41a/resourceGroups/zhqqi-sntest/providers/Microsoft.Compute/images/hello-karyon-rxnetty-all-20190125054410-ubuntu-1604"
          }
        },
        "osProfile" : {
          "computerNamePrefix" : "azureMASM-",
          "adminUsername" : "[parameters('vmUsername')]",
          "adminPassword" : "[parameters('vmPassword')]"
        },
        "networkProfile" : {
          "networkInterfaceConfigurations" : [ {
            "name" : "nic-azureMASM-st1-d11",
            "properties" : {
              "primary" : true,
              "ipConfigurations" : [ {
                "name" : "ipc-azureMASM-st1-d11",
                "properties" : {
                  "subnet" : {
                    "id" : "[parameters('subnetId')]"
                  },
                  "loadBalancerBackendAddressPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', variables('loadBalancerName'), variables('loadBalancerBackend'))]"
                  } ],
                  "loadBalancerInboundNatPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/inboundNatPools', variables('loadBalancerName'), variables('inboundNatPoolName'))]"
                  } ],
                  "applicationGatewayBackendAddressPools" : [ {
                    "id" : "[parameters('appGatewayAddressPoolId')]"
                  } ]
                }
              } ]
            }
          } ]
        }
      }
    }
  } ]
}'''

  private static String expectedCustomScriptTemplateLinux = '''{
  "$schema" : "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#",
  "contentVersion" : "1.0.0.0",
  "parameters" : {
    "location" : {
      "type" : "string",
      "metadata" : {
        "description" : "Location to deploy"
      }
    },
    "subnetId" : {
      "type" : "string",
      "metadata" : {
        "description" : "Subnet Resource ID"
      }
    },
    "appGatewayAddressPoolId" : {
      "type" : "string",
      "metadata" : {
        "description" : "App Gateway backend address pool resource ID"
      }
    },
    "vmuserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account name"
      }
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account password"
      }
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : ""
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
    "publicIPAddressName" : "pip-azureMASM-st1-d11",
    "publicIPAddressID" : "[resourceId('Microsoft.Network/publicIPAddresses', variables('publicIPAddressName'))]",
    "publicIPAddressType" : "Dynamic",
    "dnsNameForLBIP" : "dns-azuremasm-st1-d11",
    "loadBalancerBackend" : "be-azureMASM-st1-d11",
    "loadBalancerFrontEnd" : "fe-azureMASM-st1-d11",
    "loadBalancerName" : "lb-azureMASM-st1-d11",
    "loadBalancerID" : "[resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName'))]",
    "frontEndIPConfigID" : "[resourceId('Microsoft.Network/loadBalancers/frontendIPConfigurations/', variables('loadBalancerName'), variables('loadBalancerFrontEnd'))]",
    "inboundNatPoolName" : "np-azureMASM-st1-d11",
    "vhdContainerName" : "azuremasm-st1-d11",
    "osType" : {
      "publisher" : "Canonical",
      "offer" : "UbuntuServer",
      "sku" : "14.04.3-LTS",
      "version" : "latest"
    },
    "imageReference" : "[variables('osType')]",
    "uniqueStorageNameArray" : [ "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]" ]
  },
  "resources" : [ {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[concat(variables('uniqueStorageNameArray')[copyIndex()])]",
    "type" : "Microsoft.Storage/storageAccounts",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "serverGroupName" : "azureMASM-st1-d11",
      "createdTime" : "1234567890"
    },
    "copy" : {
      "name" : "storageLoop",
      "count" : 1
    },
    "properties" : {
      "accountType" : "Premium_LRS"
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('publicIPAddressName')]",
    "type" : "Microsoft.Network/publicIPAddresses",
    "location" : "[parameters('location')]",
    "tags" : null,
    "properties" : {
      "publicIPAllocationMethod" : "[variables('publicIPAddressType')]",
      "dnsSettings" : {
        "domainNameLabel" : "[variables('dnsNameForLBIP')]"
      }
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('loadBalancerName')]",
    "type" : "Microsoft.Network/loadBalancers",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "createdTime" : "1234567890",
      "cluster" : "azureMASM-st1-d11",
      "serverGroup" : "azureMASM-st1-d11"
    },
    "dependsOn" : [ "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]" ],
    "properties" : {
      "frontendIPConfigurations" : [ {
        "name" : "[variables('loadBalancerFrontEnd')]",
        "properties" : {
          "publicIpAddress" : {
            "id" : "[variables('publicIPAddressID')]"
          }
        }
      } ],
      "backendAddressPools" : [ {
        "name" : "[variables('loadBalancerBackEnd')]"
      } ],
      "inboundNatPools" : [ {
        "name" : "InboundPortConfig",
        "properties" : {
          "frontendIPConfiguration" : {
            "id" : "[variables('frontEndIPConfigID')]"
          },
          "protocol" : "tcp",
          "frontendPortRangeStart" : 50000,
          "frontendPortRangeEnd" : 50099,
          "backendPort" : 22
        }
      } ]
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "loadBalancerName" : "lb-azureMASM-st1-d11",
      "hasNewSubnet" : "false",
      "imageIsCustom" : "false",
      "storageAccountNames" : "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]"
    },
    "dependsOn" : [ "[concat('Microsoft.Storage/storageAccounts/', variables('uniqueStorageNameArray')[0])]", "[concat('Microsoft.Network/loadBalancers/', variables('loadBalancerName'))]" ],
    "sku" : {
      "name" : "Standard_A1",
      "tier" : "Standard",
      "capacity" : 2
    },
    "properties" : {
      "upgradePolicy" : {
        "mode" : "Manual"
      },
      "virtualMachineProfile" : {
        "storageProfile" : {
          "osDisk" : {
            "name" : "osdisk-azureMASM-st1-d11",
            "caching" : "ReadOnly",
            "createOption" : "FromImage",
            "vhdContainers" : [ "[concat('https://', variables('uniqueStorageNameArray')[0], '.blob.core.windows.net/', variables('vhdContainerName'))]" ]
          },
          "imageReference" : "[variables('imageReference')]"
        },
        "osProfile" : {
          "computerNamePrefix" : "azureMASM-",
          "adminUsername" : "[parameters('vmUsername')]",
          "adminPassword" : "[parameters('vmPassword')]"
        },
        "networkProfile" : {
          "networkInterfaceConfigurations" : [ {
            "name" : "nic-azureMASM-st1-d11",
            "properties" : {
              "primary" : true,
              "ipConfigurations" : [ {
                "name" : "ipc-azureMASM-st1-d11",
                "properties" : {
                  "subnet" : {
                    "id" : "[parameters('subnetId')]"
                  },
                  "loadBalancerBackendAddressPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', variables('loadBalancerName'), variables('loadBalancerBackend'))]"
                  } ],
                  "loadBalancerInboundNatPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/inboundNatPools', variables('loadBalancerName'), variables('inboundNatPoolName'))]"
                  } ],
                  "applicationGatewayBackendAddressPools" : [ {
                    "id" : "[parameters('appGatewayAddressPoolId')]"
                  } ]
                }
              } ]
            }
          } ]
        },
        "extensionProfile" : {
          "extensions" : [ {
            "name" : "azureMASM_ext",
            "properties" : {
              "publisher" : "Microsoft.Azure.Extensions",
              "type" : "CustomScript",
              "typeHandlerVersion" : "2.0",
              "autoUpgradeMinorVersion" : true,
              "settings" : {
                "fileUris" : [ "storage1", "file2" ],
                "commandToExecute" : "mkdir mydir"
              }
            }
          } ]
        }
      }
    }
  } ]
}'''

  private static String expectedCustomScriptTemplateWindows = '''{
  "$schema" : "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#",
  "contentVersion" : "1.0.0.0",
  "parameters" : {
    "location" : {
      "type" : "string",
      "metadata" : {
        "description" : "Location to deploy"
      }
    },
    "subnetId" : {
      "type" : "string",
      "metadata" : {
        "description" : "Subnet Resource ID"
      }
    },
    "appGatewayAddressPoolId" : {
      "type" : "string",
      "metadata" : {
        "description" : "App Gateway backend address pool resource ID"
      }
    },
    "vmuserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account name"
      }
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account password"
      }
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : ""
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
    "publicIPAddressName" : "pip-azureMASM-st1-d11",
    "publicIPAddressID" : "[resourceId('Microsoft.Network/publicIPAddresses', variables('publicIPAddressName'))]",
    "publicIPAddressType" : "Dynamic",
    "dnsNameForLBIP" : "dns-azuremasm-st1-d11",
    "loadBalancerBackend" : "be-azureMASM-st1-d11",
    "loadBalancerFrontEnd" : "fe-azureMASM-st1-d11",
    "loadBalancerName" : "lb-azureMASM-st1-d11",
    "loadBalancerID" : "[resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName'))]",
    "frontEndIPConfigID" : "[resourceId('Microsoft.Network/loadBalancers/frontendIPConfigurations/', variables('loadBalancerName'), variables('loadBalancerFrontEnd'))]",
    "inboundNatPoolName" : "np-azureMASM-st1-d11",
    "vhdContainerName" : "azuremasm-st1-d11",
    "osType" : {
      "publisher" : "Canonical",
      "offer" : "UbuntuServer",
      "sku" : "14.04.3-LTS",
      "version" : "latest"
    },
    "imageReference" : "[variables('osType')]",
    "uniqueStorageNameArray" : [ "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]" ]
  },
  "resources" : [ {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[concat(variables('uniqueStorageNameArray')[copyIndex()])]",
    "type" : "Microsoft.Storage/storageAccounts",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "serverGroupName" : "azureMASM-st1-d11",
      "createdTime" : "1234567890"
    },
    "copy" : {
      "name" : "storageLoop",
      "count" : 1
    },
    "properties" : {
      "accountType" : "Premium_LRS"
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('publicIPAddressName')]",
    "type" : "Microsoft.Network/publicIPAddresses",
    "location" : "[parameters('location')]",
    "tags" : null,
    "properties" : {
      "publicIPAllocationMethod" : "[variables('publicIPAddressType')]",
      "dnsSettings" : {
        "domainNameLabel" : "[variables('dnsNameForLBIP')]"
      }
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('loadBalancerName')]",
    "type" : "Microsoft.Network/loadBalancers",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "createdTime" : "1234567890",
      "cluster" : "azureMASM-st1-d11",
      "serverGroup" : "azureMASM-st1-d11"
    },
    "dependsOn" : [ "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]" ],
    "properties" : {
      "frontendIPConfigurations" : [ {
        "name" : "[variables('loadBalancerFrontEnd')]",
        "properties" : {
          "publicIpAddress" : {
            "id" : "[variables('publicIPAddressID')]"
          }
        }
      } ],
      "backendAddressPools" : [ {
        "name" : "[variables('loadBalancerBackEnd')]"
      } ],
      "inboundNatPools" : [ {
        "name" : "InboundPortConfig",
        "properties" : {
          "frontendIPConfiguration" : {
            "id" : "[variables('frontEndIPConfigID')]"
          },
          "protocol" : "tcp",
          "frontendPortRangeStart" : 50000,
          "frontendPortRangeEnd" : 50099,
          "backendPort" : 3389
        }
      } ]
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "loadBalancerName" : "lb-azureMASM-st1-d11",
      "hasNewSubnet" : "false",
      "imageIsCustom" : "false",
      "storageAccountNames" : "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]"
    },
    "dependsOn" : [ "[concat('Microsoft.Storage/storageAccounts/', variables('uniqueStorageNameArray')[0])]", "[concat('Microsoft.Network/loadBalancers/', variables('loadBalancerName'))]" ],
    "sku" : {
      "name" : "Standard_A1",
      "tier" : "Standard",
      "capacity" : 2
    },
    "properties" : {
      "upgradePolicy" : {
        "mode" : "Manual"
      },
      "virtualMachineProfile" : {
        "storageProfile" : {
          "osDisk" : {
            "name" : "osdisk-azureMASM-st1-d11",
            "caching" : "ReadOnly",
            "createOption" : "FromImage",
            "vhdContainers" : [ "[concat('https://', variables('uniqueStorageNameArray')[0], '.blob.core.windows.net/', variables('vhdContainerName'))]" ]
          },
          "imageReference" : "[variables('imageReference')]"
        },
        "osProfile" : {
          "computerNamePrefix" : "azureMASM-",
          "adminUsername" : "[parameters('vmUsername')]",
          "adminPassword" : "[parameters('vmPassword')]"
        },
        "networkProfile" : {
          "networkInterfaceConfigurations" : [ {
            "name" : "nic-azureMASM-st1-d11",
            "properties" : {
              "primary" : true,
              "ipConfigurations" : [ {
                "name" : "ipc-azureMASM-st1-d11",
                "properties" : {
                  "subnet" : {
                    "id" : "[parameters('subnetId')]"
                  },
                  "loadBalancerBackendAddressPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', variables('loadBalancerName'), variables('loadBalancerBackend'))]"
                  } ],
                  "loadBalancerInboundNatPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/inboundNatPools', variables('loadBalancerName'), variables('inboundNatPoolName'))]"
                  } ],
                  "applicationGatewayBackendAddressPools" : [ {
                    "id" : "[parameters('appGatewayAddressPoolId')]"
                  } ]
                }
              } ]
            }
          } ]
        },
        "extensionProfile" : {
          "extensions" : [ {
            "name" : "azureMASM_ext",
            "properties" : {
              "publisher" : "Microsoft.Compute",
              "type" : "CustomScriptExtension",
              "typeHandlerVersion" : "1.8",
              "autoUpgradeMinorVersion" : true,
              "settings" : {
                "fileUris" : [ "storage1", "file2" ],
                "commandToExecute" : "mkdir mydir"
              }
            }
          } ]
        }
      }
    }
  } ]
}'''

  private static String expectedCustomDataTemplate = '''{
  "$schema" : "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#",
  "contentVersion" : "1.0.0.0",
  "parameters" : {
    "location" : {
      "type" : "string",
      "metadata" : {
        "description" : "Location to deploy"
      }
    },
    "subnetId" : {
      "type" : "string",
      "metadata" : {
        "description" : "Subnet Resource ID"
      }
    },
    "appGatewayAddressPoolId" : {
      "type" : "string",
      "metadata" : {
        "description" : "App Gateway backend address pool resource ID"
      }
    },
    "vmuserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account name"
      }
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "default VM account password"
      }
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : ""
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
    "publicIPAddressName" : "pip-azureMASM-st1-d11",
    "publicIPAddressID" : "[resourceId('Microsoft.Network/publicIPAddresses', variables('publicIPAddressName'))]",
    "publicIPAddressType" : "Dynamic",
    "dnsNameForLBIP" : "dns-azuremasm-st1-d11",
    "loadBalancerBackend" : "be-azureMASM-st1-d11",
    "loadBalancerFrontEnd" : "fe-azureMASM-st1-d11",
    "loadBalancerName" : "lb-azureMASM-st1-d11",
    "loadBalancerID" : "[resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName'))]",
    "frontEndIPConfigID" : "[resourceId('Microsoft.Network/loadBalancers/frontendIPConfigurations/', variables('loadBalancerName'), variables('loadBalancerFrontEnd'))]",
    "inboundNatPoolName" : "np-azureMASM-st1-d11",
    "vhdContainerName" : "azuremasm-st1-d11",
    "osType" : {
      "publisher" : "Canonical",
      "offer" : "UbuntuServer",
      "sku" : "14.04.3-LTS",
      "version" : "latest"
    },
    "imageReference" : "[variables('osType')]",
    "uniqueStorageNameArray" : [ "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]" ]
  },
  "resources" : [ {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[concat(variables('uniqueStorageNameArray')[copyIndex()])]",
    "type" : "Microsoft.Storage/storageAccounts",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "serverGroupName" : "azureMASM-st1-d11",
      "createdTime" : "1234567890"
    },
    "copy" : {
      "name" : "storageLoop",
      "count" : 1
    },
    "properties" : {
      "accountType" : "Premium_LRS"
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('publicIPAddressName')]",
    "type" : "Microsoft.Network/publicIPAddresses",
    "location" : "[parameters('location')]",
    "tags" : null,
    "properties" : {
      "publicIPAllocationMethod" : "[variables('publicIPAddressType')]",
      "dnsSettings" : {
        "domainNameLabel" : "[variables('dnsNameForLBIP')]"
      }
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "[variables('loadBalancerName')]",
    "type" : "Microsoft.Network/loadBalancers",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "createdTime" : "1234567890",
      "cluster" : "azureMASM-st1-d11",
      "serverGroup" : "azureMASM-st1-d11"
    },
    "dependsOn" : [ "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]" ],
    "properties" : {
      "frontendIPConfigurations" : [ {
        "name" : "[variables('loadBalancerFrontEnd')]",
        "properties" : {
          "publicIpAddress" : {
            "id" : "[variables('publicIPAddressID')]"
          }
        }
      } ],
      "backendAddressPools" : [ {
        "name" : "[variables('loadBalancerBackEnd')]"
      } ],
      "inboundNatPools" : [ {
        "name" : "InboundPortConfig",
        "properties" : {
          "frontendIPConfiguration" : {
            "id" : "[variables('frontEndIPConfigID')]"
          },
          "protocol" : "tcp",
          "frontendPortRangeStart" : 50000,
          "frontendPortRangeEnd" : 50099,
          "backendPort" : 22
        }
      } ]
    }
  }, {
    "apiVersion" : "[variables('apiVersion')]",
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "loadBalancerName" : "lb-azureMASM-st1-d11",
      "hasNewSubnet" : "false",
      "imageIsCustom" : "false",
      "storageAccountNames" : "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]"
    },
    "dependsOn" : [ "[concat('Microsoft.Storage/storageAccounts/', variables('uniqueStorageNameArray')[0])]", "[concat('Microsoft.Network/loadBalancers/', variables('loadBalancerName'))]" ],
    "sku" : {
      "name" : "Standard_A1",
      "tier" : "Standard",
      "capacity" : 2
    },
    "properties" : {
      "upgradePolicy" : {
        "mode" : "Manual"
      },
      "virtualMachineProfile" : {
        "storageProfile" : {
          "osDisk" : {
            "name" : "osdisk-azureMASM-st1-d11",
            "caching" : "ReadOnly",
            "createOption" : "FromImage",
            "vhdContainers" : [ "[concat('https://', variables('uniqueStorageNameArray')[0], '.blob.core.windows.net/', variables('vhdContainerName'))]" ]
          },
          "imageReference" : "[variables('imageReference')]"
        },
        "osProfile" : {
          "computerNamePrefix" : "azureMASM-",
          "adminUsername" : "[parameters('vmUsername')]",
          "adminPassword" : "[parameters('vmPassword')]",
          "customData" : "[base64(parameters('customData'))]"
        },
        "networkProfile" : {
          "networkInterfaceConfigurations" : [ {
            "name" : "nic-azureMASM-st1-d11",
            "properties" : {
              "primary" : true,
              "ipConfigurations" : [ {
                "name" : "ipc-azureMASM-st1-d11",
                "properties" : {
                  "subnet" : {
                    "id" : "[parameters('subnetId')]"
                  },
                  "loadBalancerBackendAddressPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', variables('loadBalancerName'), variables('loadBalancerBackend'))]"
                  } ],
                  "loadBalancerInboundNatPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/inboundNatPools', variables('loadBalancerName'), variables('inboundNatPoolName'))]"
                  } ],
                  "applicationGatewayBackendAddressPools" : [ {
                    "id" : "[parameters('appGatewayAddressPoolId')]"
                  } ]
                }
              } ]
            }
          } ]
        },
        "extensionProfile" : {
          "extensions" : [ {
            "name" : "azureMASM_ext",
            "properties" : {
              "publisher" : "Microsoft.Azure.Extensions",
              "type" : "CustomScript",
              "typeHandlerVersion" : "2.0",
              "autoUpgradeMinorVersion" : true,
              "settings" : {
                "fileUris" : [ "storage1", "file2" ],
                "commandToExecute" : "mkdir mydir"
              }
            }
          } ]
        }
      }
    }
  } ]
}'''

  private static String expectedParameters = """{
  "subnetId" : {
    "value" : "$subnetId"
  },
  "vmPassword" : {
    "reference" : {
      "keyVault" : {
        "id" : "/subscriptions/$subscriptionId/resourceGroups/$defaultResourceGroup/providers/Microsoft.KeyVault/vaults/$defaultVaultName"
      },
      "secretName" : "$secretName"
    }
  }
}"""

  private static final String subscriptionId = "testSubscriptionID"
  private static final String subnetId = "SubNetTestID"
  private static final String defaultResourceGroup = "defaultResourceGroup"
  private static final String defaultVaultName = "defaultKeyVault"
  private static final String secretName = "VMPassword"

}
