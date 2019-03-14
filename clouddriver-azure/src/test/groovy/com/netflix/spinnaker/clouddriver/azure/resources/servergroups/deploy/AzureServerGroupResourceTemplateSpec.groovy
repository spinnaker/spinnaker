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
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
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
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedFullTemplate
  }

  def 'should generate correct ServerGroup resource template with custom image'() {
    description = createDescription(true)
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedFullTemplateWithCustomImage
  }

  def 'generate server group template with extensions profile for linux'() {
    description = createCustomDescription()
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedCustomScriptTemplateLinux
  }

  def 'generate server group template with extension profile for windows'() {
    description = createCustomDescription(true)
    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedCustomScriptTemplateWindows
  }

  def 'generate server group template with custom data'() {
    description = createCustomDescription()

    description.osConfig.customData = "this is test custom data"

    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedCustomDataTemplate
  }

  def 'generate server group with custom availability zones'() {
    description = createCustomDescription()

    description.zones = ["1", "3"]

    String template = AzureServerGroupResourceTemplate.getTemplate(description)

    expect:
    template.replaceAll('"createdTime" : "\\d+"', '"createdTime" : "1234567890"').replace('\r', '') == expectedCustomZonesTemplate
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

    description.credentials = new AzureCredentials("", "", "", "", "", "", "", "", false)

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
    "vmUserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin username on all VMs"
      },
      "defaultValue" : ""
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin password on all VMs"
      },
      "defaultValue" : ""
    },
    "vmSshPublicKey" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "SSH public key on all VMs"
      },
      "defaultValue" : ""
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : "sample custom data"
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
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
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "hasNewSubnet" : "false",
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
          "adminUsername" : "[parameters('vmUserName')]",
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
    "vmUserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin username on all VMs"
      },
      "defaultValue" : ""
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin password on all VMs"
      },
      "defaultValue" : ""
    },
    "vmSshPublicKey" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "SSH public key on all VMs"
      },
      "defaultValue" : ""
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : "sample custom data"
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01"
  },
  "resources" : [ {
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
      "hasNewSubnet" : "false",
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
          "imageReference" : {
            "id" : "/subscriptions/faab228d-df7a-4086-991e-e81c4659d41a/resourceGroups/zhqqi-sntest/providers/Microsoft.Compute/images/hello-karyon-rxnetty-all-20190125054410-ubuntu-1604"
          }
        },
        "osProfile" : {
          "computerNamePrefix" : "azureMASM-",
          "adminUsername" : "[parameters('vmUserName')]",
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
    "vmUserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin username on all VMs"
      },
      "defaultValue" : ""
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin password on all VMs"
      },
      "defaultValue" : ""
    },
    "vmSshPublicKey" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "SSH public key on all VMs"
      },
      "defaultValue" : ""
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : "sample custom data"
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
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
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "hasNewSubnet" : "false",
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
          "adminUsername" : "[parameters('vmUserName')]",
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
    "vmUserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin username on all VMs"
      },
      "defaultValue" : ""
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin password on all VMs"
      },
      "defaultValue" : ""
    },
    "vmSshPublicKey" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "SSH public key on all VMs"
      },
      "defaultValue" : ""
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : "sample custom data"
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
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
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "hasNewSubnet" : "false",
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
          "adminUsername" : "[parameters('vmUserName')]",
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
    "vmUserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin username on all VMs"
      },
      "defaultValue" : ""
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin password on all VMs"
      },
      "defaultValue" : ""
    },
    "vmSshPublicKey" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "SSH public key on all VMs"
      },
      "defaultValue" : ""
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : "sample custom data"
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
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
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "hasNewSubnet" : "false",
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
          "adminUsername" : "[parameters('vmUserName')]",
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


  private static String expectedCustomZonesTemplate = '''{
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
    "vmUserName" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin username on all VMs"
      },
      "defaultValue" : ""
    },
    "vmPassword" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "Admin password on all VMs"
      },
      "defaultValue" : ""
    },
    "vmSshPublicKey" : {
      "type" : "securestring",
      "metadata" : {
        "description" : "SSH public key on all VMs"
      },
      "defaultValue" : ""
    },
    "customData" : {
      "type" : "string",
      "metadata" : {
        "description" : "custom data to pass down to the virtual machine(s)"
      },
      "defaultValue" : "sample custom data"
    }
  },
  "variables" : {
    "apiVersion" : "2018-10-01",
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
    "name" : "azureMASM-st1-d11",
    "type" : "Microsoft.Compute/virtualMachineScaleSets",
    "location" : "[parameters('location')]",
    "tags" : {
      "appName" : "azureMASM",
      "stack" : "st1",
      "detail" : "d11",
      "cluster" : "azureMASM-st1-d11",
      "createdTime" : "1234567890",
      "hasNewSubnet" : "false",
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
          "adminUsername" : "[parameters('vmUserName')]",
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
    },
    "zones" : [ "1", "3" ]
  } ]
}'''

}
