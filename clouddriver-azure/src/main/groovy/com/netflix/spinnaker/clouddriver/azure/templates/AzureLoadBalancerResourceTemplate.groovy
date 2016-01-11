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

package com.netflix.spinnaker.clouddriver.azure.templates

import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.UpsertAzureLoadBalancerDescription
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.map.SerializationConfig

class AzureLoadBalancerResourceTemplate {

  static ObjectMapper mapper = new ObjectMapper().configure(SerializationConfig.Feature.INDENT_OUTPUT, true)

  static String getTemplate(UpsertAzureLoadBalancerDescription description) {
    LoadBalancerTemplate template = new LoadBalancerTemplate(description)
    mapper.writeValueAsString(template)
  }

  static class LoadBalancerTemplate{
    String $schema = "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#"
    String contentVersion = "1.0.0.0"

    LoadBalancerParameters parameters
    LoadBalancerTemplateVariables variables
    ArrayList<Resource> resources = []

    LoadBalancerTemplate(UpsertAzureLoadBalancerDescription description){
      parameters = new LoadBalancerParameters()
      variables = new LoadBalancerTemplateVariables(description)

      resources.add(new PublicIpResource())

      LoadBalancer lb = new LoadBalancer(description)
      lb.addDependency(resources[0])
      resources.add(lb)
    }
  }

  static class LoadBalancerTemplateVariables{
    String loadBalancerName
    String virtualNetworkName
    String publicIPAddressName
    String publicIPAddressType = "Dynamic"
    String loadBalancerFrontEnd
    String dnsNameForLBIP
    String ipConfigName
    String loadBalancerID = "[resourceID('Microsoft.Network/loadBalancers',variables('loadBalancerName'))]"
    String publicIPAddressID = "[resourceID('Microsoft.Network/publicIPAddresses',variables('publicIPAddressName'))]"
    String frontEndIPConfig = "[concat(variables('loadBalancerID'),'/frontendIPConfigurations/',variables('loadBalancerFrontEnd'))]"

    LoadBalancerTemplateVariables(UpsertAzureLoadBalancerDescription description){
      String regionName = description.region.replace(' ', '').toLowerCase()

      loadBalancerName = description.loadBalancerName
      virtualNetworkName = "vnet-" + regionName + "-" + description.loadBalancerName
      publicIPAddressName = "publicIp-" + regionName + "-" + description.loadBalancerName
      loadBalancerFrontEnd = "lbFrontEnd-" + regionName + "-" + description.loadBalancerName
      dnsNameForLBIP = "dns-" + regionName.toLowerCase() + "-" + description.loadBalancerName.toLowerCase()
      ipConfigName = "ipConfig-" + regionName + "-" + description.loadBalancerName
    }
  }

  static class LoadBalancerParameters{
    Location location = new Location()
  }

  static class Location{
    String type = "string"
    ArrayList<String> allowedValues = ["East US", "eastus", "West US", "westus", "West Europe", "westeurope", "East Asia", "eastasia", "Southeast Asia", "southeastasia"]
    Map<String, String> metadata = ["description":"Location to deploy"]
  }

  static class LoadBalancer extends DependingResource{
    Map<String, String> tags
    LoadBalancerProperties properties

    LoadBalancer(UpsertAzureLoadBalancerDescription description) {
      apiVersion = "2015-05-01-preview"
      name = "[variables('loadBalancerName')]"
      type = "Microsoft.Network/loadBalancers"
      location = "[parameters('location')]"
      tags = ["appName":description.appName, "stack":description.stack, "detail":description.detail]

      properties = new LoadBalancerProperties(description)
    }

  }

  private static class AzureProbe {
    AzureProbeProperty properties
    String name

    AzureProbe(AzureLoadBalancerDescription.AzureLoadBalancerProbe probe) {
      properties = new AzureProbeProperty(probe)
      name = probe.probeName
    }

    private static class AzureProbeProperty {
      String protocol
      Integer port
      Integer intervalInSeconds
      String requestPath
      Integer numberOfProbes

      AzureProbeProperty(AzureLoadBalancerDescription.AzureLoadBalancerProbe probe){
        protocol = probe.probeProtocol.toString().toLowerCase()
        port = probe.probePort
        intervalInSeconds = probe.probeInterval
        numberOfProbes = probe.unhealthyThreshold
        requestPath = probe.probePath
      }
    }
  }

  static class LoadBalancerProperties{
    ArrayList<FrontEndIpConfiguration> frontEndIPConfigurations = []
    ArrayList<LoadBalancingRule> loadBalancingRules = []
    ArrayList<AzureProbe> probes = []

    LoadBalancerProperties(UpsertAzureLoadBalancerDescription description){
      frontEndIPConfigurations.add(new FrontEndIpConfiguration())
      description.loadBalancingRules?.each{loadBalancingRules.add(new LoadBalancingRule(it))}
      description.probes?.each{ probes.add(new AzureProbe(it))}
    }
  }

  static class FrontEndIpConfiguration{
    String name
    FrontEndIpProperties properties;

    FrontEndIpConfiguration()
    {
      name = "[variables('loadBalancerFrontEnd')]"
      properties = new FrontEndIpProperties("[variables('publicIPAddressID')]")
    }
  }

  static class FrontEndIpProperties{
    IdRef publicIPAddress

    FrontEndIpProperties(String id){
      publicIPAddress = new IdRef(id)
    }
  }

  static class VirtualNetworkResource extends Resource{
    String apiVersion = '2015-05-01-preview'
    String name = '''[variables('virtualNetworkName')]'''
    String type = '''Microsoft.Network/virtualNetworks'''
    String location = '''[parameters('location')]'''
    VirtualNetworkProperties properties = new VirtualNetworkProperties()
  }


  static class VirtualNetworkProperties{
    AddressSpace addressSpace = new AddressSpace();
  }

  static class AddressSpace{
    def addressPrefixes = ['''[variables('addressPrefix')]''']
    def subnets = [new Subnet()]
  }

  static class Subnet{
    def name = '''[variables('subnetName')]'''
    def properties = new SubnetProperties()
  }

  static class SubnetProperties{
    def addressPrefix = '''[variables('subnetPrefix')]'''
  }

  static class PublicIpResource extends Resource{

    PublicIpResource() {
      apiVersion = '2015-05-01-preview'
      name = '''[variables('publicIPAddressName')]'''
      type = '''Microsoft.Network/publicIPAddresses'''
      location = '''[parameters('location')]'''
    }
    PublicIPProperties properties = new PublicIPProperties()
  }

  static class PublicIPProperties{
    String publicIPAllocationMethod = '''[variables('publicIPAddressType')]'''
    DnsSettings dnsSettings = new DnsSettings()
  }

  static class DnsSettings{
    String domainNameLabel = '''[variables('dnsNameForLBIP')]'''
  }

  static class NetworkInterface extends DependingResource{

    public NetworkInterface()
    {
      apiVersion = '''2015-05-01-preview'''
      name = '''[variables('nicName')]'''
      type = '''Microsoft.Network/networkInterfaces'''
      location = '''[parameters('location')]'''
    }

    NetworkInterfaceProperties properties = new NetworkInterfaceProperties();
  }

  static class NetworkInterfaceProperties{
    ArrayList<IPConfiguration> ipConfigurations = new ArrayList<IPConfiguration>();

    public NetworkInterfaceProperties(){
      ipConfigurations.add( new IPConfiguration())
    }
  }

  static class IPConfiguration{
    String name = '''[variables('ipConfigName')]'''
    IPConfigurationProperties properties = new IPConfigurationProperties()
    ArrayList<IdRef> loadBalancerBackendAddressPools = new ArrayList<IdRef>()

    public IPConfiguration()
    {
      loadBalancerBackendAddressPools.add(new IdRef('''[concat(resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName')),'/backendAddressPools/loadBalancerBackEnd')]'''))
    }
  }

  static class IPConfigurationProperties{
    String privateIPAllocationMethod = '''Dynamic'''
    IdRef subnet = new IdRef('''[variables('subnetRefID')]''')
  }

  static class IdRef{
    String id

    public IdRef(String refID)
    {
      id = refID
    }
  }

  static class LoadBalancingRule{
    String name
    LoadBalancingRuleProperties properties

    LoadBalancingRule(AzureLoadBalancerDescription.AzureLoadBalancingRule rule){
      name = rule.ruleName
      properties = new LoadBalancingRuleProperties(rule)
    }

  }

  static class LoadBalancingRuleProperties{
    IdRef frontendIPConfiguration
    String protocol
    Integer frontendPort
    Integer backendPort
    IdRef probe

    LoadBalancingRuleProperties(AzureLoadBalancerDescription.AzureLoadBalancingRule rule){
      frontendIPConfiguration = new IdRef("[variables('frontEndIPConfig')]")
      protocol = rule.protocol.toString().toLowerCase()
      frontendPort = rule.externalPort
      backendPort = rule.backendPort
      probe = new IdRef("[concat(variables('loadBalancerID'),'/probes/" + rule.probeName + "')]")
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
