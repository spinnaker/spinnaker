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

package com.netflix.spinnaker.kato.azure.deploy.description.templates

import com.netflix.spinnaker.kato.azure.deploy.description.UpsertAzureLoadBalancerDescription
import groovy.json.JsonBuilder

class AzureLoadBalancerResourceTemplate extends AzureResourceBaseTemplate {

  private static String loadBalancerNameVar = "loadBalancerName"
  private static String loadBalancerIDVar = "loadBalancerID"
  private static String virtualNetworkNameVar = "virtualNetworkName"
  private static String virtualNetworkIdVar = "virtualNetworkID"
  private static String publicIPAddressNameVar = "publicIPAddressName"
  private static String publicIPAddressIDVar = "publicIPAddressID"
  private static String publicIPAddressTypeVar = "publicIPAddressType"
  private static String frontEndIPConfigNameVar = "loadBalancerFrontEnd"
  private static String frontEndIPConfigIDVar = "frontEndIPConfig"
  private static String loadBalancerBackEndNameVar = "loadBalancerBackEnd"
  private static String dnsNameforLBIPVar = "dnsNameForLBIP"
  private static String subnetNameVar = "subnetName"
  private static String subnetRefIdVar = "subnetRefID"
  private static String addressPrefixVar = "addressPrefix"
  private static String subnetPrefixVar = "subnetPrefix"
  private static String nicNameVar = "nicName"
  private static String nicIdVar = "nicID"
  private static String ipConfigNameVar = "ipConfigName"
  private static String backEndIPConfigNameVar = "backEndConfigID"


  static String getTemplate(UpsertAzureLoadBalancerDescription description) {
    String parameters = getParametersTemplate(description)
    String variables = getVariablesTemplate(description)
    String resources = getResourcesTemplate(description)

    String.format(baseTemplate, parameters + ",\n" + variables + ",\n" + resources)
  }

  // Define the variables that will be used
  static String getVariablesTemplate(UpsertAzureLoadBalancerDescription description) {

    String regionName = description.region.replace(' ', '')
    String networkResourceSuffix = "-" + regionName + "-" + description.loadBalancerName
    String vnetName = "vnet" + networkResourceSuffix
    String publicIPName = "publicIp" + networkResourceSuffix
    String frontEndIPConfigName = "lbFrontEnd" + networkResourceSuffix
    String backEndName = "lbBackEnd" + networkResourceSuffix

    // DNS names must be lower case
    String dnsName = "dns" + networkResourceSuffix.toLowerCase()
    String nic = "nic" + networkResourceSuffix
    String ipConfigName = "ipConfig" + networkResourceSuffix
    String subnetName = "subnet" + networkResourceSuffix

    StringBuilder variables = new StringBuilder()

    // Add "name" variable
    variables.append(addVarEntry(String.format(variableEntry, loadBalancerNameVar, description.loadBalancerName ) + ","))
    variables.append(addVarEntry(String.format(variableEntry, virtualNetworkNameVar, vnetName) + ","))
    variables.append(addVarEntry(String.format(variableEntry, publicIPAddressNameVar, publicIPName) + ","))
    variables.append(addVarEntry(String.format(variableEntry, publicIPAddressTypeVar, "Dynamic") + ","))
    variables.append(addVarEntry(String.format(variableEntry, loadBalancerBackEndNameVar, backEndName) + ","))
    variables.append(addVarEntry(String.format(variableEntry, frontEndIPConfigNameVar, frontEndIPConfigName) + ","))
    variables.append(addVarEntry(String.format(variableEntry, dnsNameforLBIPVar, dnsName) + ","))
    variables.append(addVarEntry(String.format(variableEntry, subnetNameVar, subnetName) + ","))
    variables.append(addVarEntry(String.format(variableEntry, subnetPrefixVar, "10.0.0.0/24") + ","))
    variables.append(addVarEntry(String.format(variableEntry, addressPrefixVar, "10.0.0.0/16") + ","))
    variables.append(addVarEntry(String.format(variableEntry, nicNameVar, nic) + ","))
    variables.append(addVarEntry(String.format(variableEntry, ipConfigNameVar, ipConfigName) + ","))
    variables.append(addVarEntry(String.format(variableEntry, backEndIPConfigNameVar, ipConfigName) + ","))

    // Add "id" variables
    variables.append(addVarEntry(String.format(variableEntry, loadBalancerIDVar, String.format(resourceIdLookupString, networkLoadBalancerType, loadBalancerNameVar)) + ","))
    variables.append(addVarEntry(String.format(variableEntry, virtualNetworkIdVar, String.format(resourceIdLookupString, networkVNetType, virtualNetworkNameVar)) + ","))
    variables.append(addVarEntry(String.format(variableEntry, publicIPAddressIDVar, String.format(resourceIdLookupString, networkIPAddressesType, publicIPAddressNameVar)) + ","))
    variables.append(addVarEntry(String.format(variableEntry, frontEndIPConfigIDVar, getFrontEndIPConfigIDLookup()) + ","))
    variables.append(addVarEntry(String.format(variableEntry, subnetRefIdVar, getSubnetRefID()) + ","))
    variables.append(addVarEntry(String.format(variableEntry, nicIdVar, String.format(resourceIdLookupString, networkNicType, nicNameVar))))

    String.format(baseVariablesTemplate, variables.toString())
  }

  protected static String getParametersTemplate(UpsertAzureLoadBalancerDescription description) {
    String.format(baseParametersTemplate, locationParameter)
  }

  protected static String getResourcesTemplate(UpsertAzureLoadBalancerDescription description) {
    StringBuilder resources = new StringBuilder();
    resources.append(getResourceEntry(getPublicIPAddressResource(), "," ))
    resources.append(getResourceEntry(getVirtualNetworkResource(), ","))
    resources.append(getResourceEntry(getNetworkInterfaceResource(), ","))
    resources.append(getResourceEntry(getLoadBalancerResource(description),""))

    String.format(baseResourceTemplate, resources.toString())
  }

  // get resource entries
  private static String getLoadBalancerResource(UpsertAzureLoadBalancerDescription description) {
    StringBuilder loadBalancers = new StringBuilder()
    loadBalancers.append(String.format(resourceHeader, apiVersion, String.format(variableString, loadBalancerNameVar), networkLoadBalancerType))
    loadBalancers.append(String.format(dependsOn, String.format(concatString, "'" + networkIPAddressesType + "/'", String.format(variableString, publicIPAddressNameVar ))))
    loadBalancers.append(String.format("      \"tags\": {\n        \"stack\": \"%s\",\n        \"detail\": \"%s\" \n      },\n", description.stack, description.detail))
    StringBuilder properties = new StringBuilder()
    properties.append(getFrontEndIPConfigurationProperty())
    properties.append(",\n")
    properties.append(getLoadBalancerBackendPoolsProperty())
    properties.append(",\n")
    properties.append(getLoadBalancerRulesProperty(description))
    properties.append(",\n")
    properties.append(getProbesProperty(description))
    properties.append("\n")
    loadBalancers.append(String.format(resourceProperties, properties.toString()))

    loadBalancers.toString()
  }

  private static String getVirtualNetworkResource() {
    StringBuilder virtualNetworks = new StringBuilder()
    String virtualNetworkResourceName = String.format(variableString, virtualNetworkNameVar)
    virtualNetworks.append(String.format(resourceHeader, apiVersion, virtualNetworkResourceName, networkVNetType, frontEndIPConfigNameVar))
    virtualNetworks.append(virtualNetworkProperties)

    virtualNetworks.toString()
  }

  private static String getNetworkInterfaceResource() {
    StringBuilder networkInterfaces = new StringBuilder()
    String networkInterfaceResourceName = String.format(variableString, nicNameVar)
    networkInterfaces.append(String.format(resourceHeader, apiVersion, networkInterfaceResourceName, networkNicType))
    networkInterfaces.append(String.format(dependsOn, String.format(concatString, "'" + networkVNetType + "/'", String.format(variableString, virtualNetworkNameVar ))))
    networkInterfaces.append(String.format(networkInterfaceProperties))

    networkInterfaces.toString()
  }

  private static String getPublicIPAddressResource() {
    StringBuilder publicIPAddresses = new StringBuilder()
    String publicIPResourceName = String.format(variableString, publicIPAddressNameVar)
    publicIPAddresses.append(String.format(resourceHeader, apiVersion, publicIPResourceName, networkIPAddressesType))
    publicIPAddresses.append(publicIPAddressProperties)

    publicIPAddresses.toString()
  }

  private static String getLoadBalancerRulesProperty(UpsertAzureLoadBalancerDescription description) {
    StringBuilder loadBalancerRules = new StringBuilder()
    if (!description.loadBalancingRules) {
      return ""
    }

    int i = 0
    while (i < description.loadBalancingRules.size()) {
      UpsertAzureLoadBalancerDescription.AzureLoadBalancingRule r = description.loadBalancingRules[i]
      String loadBalancerRule = String.format(loadBalancerRuleProperty, r.ruleName, r.protocol.toString().toLowerCase(), r.externalPort, r.backendPort, r.probeName )
      loadBalancerRules.append(loadBalancerRule)

      if (i < description.loadBalancingRules.size() - 1) {
        loadBalancerRules.append(",\n")
      }
      i++
    }
    loadBalancerRulePropertyArrayHeader + String.format(resourceArrayString, loadBalancerRules.toString())
  }

  private static String getFrontEndIPConfigurationProperty() {
    frontEndIPConfigurationProperty
  }

  private static String getLoadBalancerBackendPoolsProperty() {
    backEndPoolProperty
  }

  private static String getFrontEndIPConfigIDLookup() {
    String.format(concatString, String.format(variableString, loadBalancerIDVar), "'/frontendIPConfigurations/'," + String.format(variableString, frontEndIPConfigNameVar))
  }

  private static String getBackendAddressPool() {
    "[concat(resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName')),'/backendAddressPools/loadBalancerBackEnd')]"

    //String.format(concatString, String.format(variableString, loadBalancerIDVar), "'/ipConfigurations/'," + String.format(variableString, loadBalancerBackEndNameVar))
    }

  private static String getSubnetRefID() {
    String.format(concatString, String.format(variableString, virtualNetworkIdVar), "'/subnets/'," + String.format(variableString, subnetNameVar))
  }


  private static String getProbesProperty(UpsertAzureLoadBalancerDescription description) {
    StringBuilder probes = new StringBuilder()
    int i = 0
    while (i < description.probes.size()) {
      UpsertAzureLoadBalancerDescription.AzureLoadBalancerProbe p = description.probes[i]
      probes.append(getProbeDefinition(p))
      if (i < description.probes.size() -1) {
        probes.append(",\n")
      }
      i++
    }
    probePropertyArraryHeader + String.format(resourceArrayString, probes.toString())
  }

  private static String getProbeDefinition(UpsertAzureLoadBalancerDescription.AzureLoadBalancerProbe probe) {
    AzureProbe p = new AzureProbe(name: probe.probeName)
    p.properties.intervalInSeconds = probe.probeInterval
    p.properties.numberOfProbes = probe.unhealthyThreshold
    p.properties.port = probe.probePort
    p.properties.requestPath = probe.probePath
    p.properties.protocol = probe.probeProtocol.name().toLowerCase()

    new JsonBuilder(p).toString()
  }

  private static networkIPAddressesType = "Microsoft.Network/publicIPAddresses"
  private static networkLoadBalancerType = "Microsoft.Network/loadBalancers"
  private static networkVNetType = "Microsoft.Network/virtualNetworks"
  private static networkNicType = "Microsoft.Network/networkInterfaces"

  private static String frontEndIPConfigurationProperty = "        \"frontEndIPConfigurations\": [\n" +
    "          {\n" +
    "            \"name\": \"[variables('" + frontEndIPConfigNameVar + "')]\",\n" +
    "            \"properties\": {\n" +
    "              \"publicIPAddress\": {\n" +
    "                \"id\": \"[variables('" + publicIPAddressIDVar + "')]\"\n" +
    "              }\n" +
    "            }\n" +
    "          }\n" +
    "        ]"

  private static String backEndPoolProperty = "        \"backendAddressPools\": [\n" +
    "          {\n" +
    "            \"name\": \"loadBalancerBackEnd\"\n" +
    "          }\n" +
    "        ]"

  private static String loadBalancerRulePropertyArrayHeader = "        \"loadBalancingRules\":"
  private static String loadBalancerRuleProperty = "          {\n" +
    "            \"name\": \"%s\",\n" +
    "            \"properties\": {\n" +
    "              \"frontendIPConfiguration\": {\n" +
    "                \"id\": \"[variables('" + frontEndIPConfigIDVar + "')]\"\n" +
    "              },\n" +
    "              \"backendAddressPool\": {\n" +
    "                \"id\": \"" + getBackendAddressPool() + "\"\n" +
    "              },\n" +
    "              \"protocol\": \"%s\",\n" +
    "              \"frontendPort\": %s,\n" +
    "              \"backendPort\": %s,\n" +
    "              \"probe\": {\n" +
    "                \"id\": \"[concat(variables('" + loadBalancerIDVar + "'),'/probes/%s')]\"\n" +
    "              }\n" +
    "            }\n" +
    "          }\n"

  private static String probePropertyArraryHeader = "        \"probes\": "
  private static String probeProperty =  "          {\n" +
    "            \"name\": \"%s\",\n" +
    "            \"properties\": {\n" +
    "              \"protocol\": \"%s\",\n" +
    "              \"port\": %s,\n" +
    "              \"intervalInSeconds\": %s,\n" +
    "              \"numberOfProbes\": %s,\n" +
    "              \"requestPath\": \"%s\"\n" +
    "            }\n" +
    "          }"

  private static String networkInterfaceProperties = "      \"properties\": {\n" +
    "        \"ipConfigurations\": [\n" +
    "          {\n" +
    "            \"name\": \"[variables('" + ipConfigNameVar + "')]\",\n" +
    "            \"properties\": {\n" +
    "              \"privateIPAllocationMethod\": \"Dynamic\",\n" +
    "              \"subnet\": {\n" +
    "                \"id\": \"[variables('" + subnetRefIdVar + "')]\"\n" +
    "              }\n" +
    "            },\n" +
    "            \"loadBalancerBackendAddressPools\": [\n" +
    "              {\n" +
    "                \"id\": \"" + getBackendAddressPool() + "\"\n" +
    "              }\n" +
    "            ]\n" +
    "          }\n" +
    "        ]\n" +
    "      }"

  private static String virtualNetworkProperties = "      \"properties\": {\n" +
    "        \"addressSpace\": {\n" +
    "          \"addressPrefixes\": [\n" +
    "            \"[variables('" + addressPrefixVar + "')]\"\n" +
    "          ]\n" +
    "        },\n" +
    "        \"subnets\": [\n" +
    "          {\n" +
    "            \"name\": \"[variables('" + subnetNameVar + "')]\",\n" +
    "            \"properties\": {\n" +
    "              \"addressPrefix\": \"[variables('" + subnetPrefixVar + "')]\"\n" +
    "            }\n" +
    "          }\n" +
    "        ]\n" +
    "      }"

  private static String publicIPAddressProperties = "      \"properties\": {\n" +
    "        \"publicIPAllocationMethod\": \"[variables('" + publicIPAddressTypeVar + "')]\",\n" +
    "        \"dnsSettings\": {\n" +
    "          \"domainNameLabel\": \"[variables('" + dnsNameforLBIPVar + "')]\"\n" +
    "        }\n" +
    "      }"

  private static String locationParameter = "    \"location\": {\n" +
    "      \"type\": \"string\",\n" +
    "      \"allowedValues\": [\n" +
    "        \"eastus\",\n" +
    "        \"westus\",\n" +
    "        \"westeurope\",\n" +
    "        \"eastasia\",\n" +
    "        \"southeastasia\"\n" +
    "      ],\n" +
    "      \"metadata\": {\n" +
    "        \"description\": \"Location to deploy\"\n" +
    "      }\n" +
    "    }"

  private static class AzureProbe {
    String name
    AzureProbeProperty properties

    AzureProbe() {
      properties = new AzureProbeProperty()
    }

    private static class AzureProbeProperty {
      String protocol
      Integer port
      Integer intervalInSeconds
      Integer numberOfProbes
      String requestPath
    }
  }
}
 /*
  {
      "apiVersion": "2015-05-01-preview",
      "name": "[variables('loadBalancerName')]",
      "type": "Microsoft.Network/loadBalancers",
      "location": "[parameters('location')]",
      "dependsOn": [
        "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName1'))]",
        "[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName2'))]"
      ],
      "properties": {
        "frontendIPConfigurations": [
          {
            "name": "loadBalancerFrontEnd1",
            "properties": {
              "publicIPAddress": {
                "id": "[variables('publicIPAddressID1')]"
              }
            }
          },
          {
            "name": "loadBalancerFrontEnd2",
            "properties": {
              "publicIPAddress": {
                "id": "[variables('publicIPAddressID2')]"
              }
            }
          }
        ],
        "backendAddressPools": [
          {
            "name": "loadBalancerBackEnd"
          }
        ],
        "loadBalancingRules": [
          {
            "name": "LBRuleForVIP1",
            "properties": {
              "frontendIPConfiguration": {
                "id": "[variables('frontEndIPConfigID1')]"
              },
              "backendAddressPool": {
                "id": "[variables('lbBackendPoolID')]"
              },
              "protocol": "tcp",
              "frontendPort": 443,
              "backendPort": 443,
              "probe": {
                "id": "[variables('lbProbeID')]"
              }
            }
          },
          {
            "name": "LBRuleForVIP2",
            "properties": {
              "frontendIPConfiguration": {
                "id": "[variables('frontEndIPConfigID2')]"
              },
              "backendAddressPool": {
                "id": "[variables('lbBackendPoolID')]"
              },
              "protocol": "tcp",
              "frontendPort": 443,
              "backendPort": 444,
              "probe": {
                "id": "[variables('lbProbeID')]"
              }
            }
          }
        ],
        "probes": [
          {
            "name": "tcpProbe",
            "properties": {
              "protocol": "tcp",
              "port": 445,
              "intervalInSeconds": 5,
              "numberOfProbes": 2
            }
          }
        ]
      }
    }
 */

