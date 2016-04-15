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

import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage
import com.netflix.spinnaker.clouddriver.azure.templates.AzureServerGroupResourceTemplate
import spock.lang.Specification

class AzureServerGroupResourceTemplateSpec extends Specification {
  AzureServerGroupDescription description

  void setup() {
    description = createDescription(false)
  }

  def 'should generate correct ServerGroup resource template'() {
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect: template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"') == expectedFullTemplate
  }

  def 'should generate correct ServerGroup resource template with custom image'() {
    description = createDescription(true)
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect: template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"') == expectedFullTemplateWithCustomImage
  }

  private static AzureServerGroupDescription createDescription(boolean withCustomImage) {
    AzureServerGroupDescription description = new AzureServerGroupDescription()
    description.name = 'azureMASM-st1-d11'
    description.cloudProvider = 'azure'
    description.application = 'azureMASM'
    description.stack = 'st1'
    description.detail = 'd11'
    description.clusterName = description.getClusterName()
    description.region = 'westus'
    description.user = '[anonymous]'
    description.loadBalancerName = 'azureMASM-st1-d11'

    description.upgradePolicy = AzureServerGroupDescription.UpgradePolicy.Manual

    AzureNamedImage image = new AzureNamedImage()
    if (withCustomImage) {
      image.isCustom = true
      image.ostype = 'Linux'
      image.region = 'westus'
      image.uri = 'https://storevm112345.blob.core.windows.net/vhds/vm1-1234520161917555.vhd'
    } else {
      image.sku = '15.04'
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
    config.adminUserName = 'spinnaker_admin'
    config.adminPassword = 'sp!nn*K3r'
    description.osConfig = config

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
    }
  },
  "variables" : {
    "vhdContainerName" : "azuremasm-st1-d11",
    "osType" : {
      "publisher" : "Canonical",
      "offer" : "UbuntuServer",
      "sku" : "15.04",
      "version" : "latest"
    },
    "imageReference" : "[variables('osType')]",
    "uniqueStorageNameArray" : [ "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]" ]
  },
  "resources" : [ {
    "apiVersion" : "2015-06-15",
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
      "accountType" : "Standard_LRS"
    }
  }, {
    "apiVersion" : "2015-06-15",
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "loadBalancerName" : "azureMASM-st1-d11",
      "imageIsCustom" : "false",
      "storageAccountNames" : "[concat(uniqueString(concat(resourceGroup().id, subscription().id, 'azuremasmst1d11', '0')), 'sa')]"
    },
    "dependsOn" : [ "[concat('Microsoft.Storage/storageAccounts/', variables('uniqueStorageNameArray')[0])]" ],
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
          "adminUserName" : "spinnaker_admin",
          "adminPassword" : "sp!nn*K3r"
        },
        "networkProfile" : {
          "networkInterfaceConfigurations" : [ {
            "name" : "nic-azureMASM-st1-d11",
            "properties" : {
              "primary" : "true",
              "ipConfigurations" : [ {
                "name" : "ipc-azureMASM-st1-d11",
                "properties" : {
                  "subnet" : {
                    "id" : "[parameters('subnetId')]"
                  },
                  "loadBalancerBackendAddressPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', 'azureMASM-st1-d11', 'be-azureMASM-st1-d11')]"
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
    }
  },
  "variables" : { },
  "resources" : [ {
    "apiVersion" : "2015-06-15",
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "loadBalancerName" : "azureMASM-st1-d11",
      "imageIsCustom" : "true"
    },
    "dependsOn" : [ ],
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
            "osType" : "Linux",
            "image" : {
              "uri" : "https://storevm112345.blob.core.windows.net/vhds/vm1-1234520161917555.vhd"
            }
          }
        },
        "osProfile" : {
          "computerNamePrefix" : "azureMASM-",
          "adminUserName" : "spinnaker_admin",
          "adminPassword" : "sp!nn*K3r"
        },
        "networkProfile" : {
          "networkInterfaceConfigurations" : [ {
            "name" : "nic-azureMASM-st1-d11",
            "properties" : {
              "primary" : "true",
              "ipConfigurations" : [ {
                "name" : "ipc-azureMASM-st1-d11",
                "properties" : {
                  "subnet" : {
                    "id" : "[parameters('subnetId')]"
                  },
                  "loadBalancerBackendAddressPools" : [ {
                    "id" : "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', 'azureMASM-st1-d11', 'be-azureMASM-st1-d11')]"
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

}
