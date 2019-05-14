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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription

class AzureLoadBalancerResourceTemplate {

  static ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)

  static final String DEFAULT_BACKEND_POOL = "default_LB_BAP"

  static String getTemplate(AzureLoadBalancerDescription description) {
    LoadBalancerTemplate template = new LoadBalancerTemplate(description)
    mapper.writeValueAsString(template)
  }

  static class LoadBalancerTemplate{
    //TODO: Make this configurable for AZURE_US_GOVERNMENT
    String $schema = "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#"
    String contentVersion = "1.0.0.0"

    LoadBalancerParameters parameters
    LoadBalancerTemplateVariables variables
    ArrayList<Resource> resources = []

    LoadBalancerTemplate(AzureLoadBalancerDescription description){
      parameters = new LoadBalancerParameters()
      variables = new LoadBalancerTemplateVariables(description)

      def publicIp = new PublicIpResource(properties: new PublicIPPropertiesWithDns())
      publicIp.sku = new Sku("Standard")
      publicIp.properties.publicIPAllocationMethod = "Static"
      resources.add(publicIp)

      LoadBalancer lb = new LoadBalancer(description)
      lb.addDependency(resources[0])
      resources.add(lb)
    }
  }

  static class LoadBalancerTemplateVariables{
    String apiVersion = "2018-08-01"
    String loadBalancerName
    String virtualNetworkName
    String publicIPAddressName
    String loadBalancerFrontEnd
    String loadBalancerBackEnd
    String dnsNameForLBIP
    String ipConfigName
    String loadBalancerID = "[resourceID('Microsoft.Network/loadBalancers',variables('loadBalancerName'))]"
    String publicIPAddressID = "[resourceID('Microsoft.Network/publicIPAddresses',variables('publicIPAddressName'))]"
    String frontEndIPConfig = "[concat(variables('loadBalancerID'),'/frontendIPConfigurations/',variables('loadBalancerFrontEnd'))]"
    String backendPoolID = "[concat(variables('loadBalancerID'),'/backendAddressPools/',variables('loadBalancerBackEnd'))]"

    LoadBalancerTemplateVariables(AzureLoadBalancerDescription description){
      String regionName = description.region.replace(' ', '').toLowerCase()
      String resourceGroupName = AzureUtilities.getResourceGroupName(description)

      loadBalancerName = description.loadBalancerName.toLowerCase()
      virtualNetworkName = AzureUtilities.VNET_NAME_PREFIX + resourceGroupName.toLowerCase()
      publicIPAddressName = AzureUtilities.PUBLICIP_NAME_PREFIX + description.loadBalancerName.toLowerCase()
      loadBalancerFrontEnd = AzureUtilities.LBFRONTEND_NAME_PREFIX + description.loadBalancerName.toLowerCase()
      loadBalancerBackEnd = DEFAULT_BACKEND_POOL
      dnsNameForLBIP = DnsSettings.getUniqueDNSName(description.loadBalancerName.toLowerCase())
      ipConfigName = AzureUtilities.IPCONFIG_NAME_PREFIX + description.loadBalancerName.toLowerCase()
    }
  }

  static class LoadBalancerParameters{
    Location location = new Location()
  }

  static class Location{
    String type = "string"
    Map<String, String> metadata = ["description":"Location to deploy"]
  }

  static class LoadBalancer extends DependingResource{
    LoadBalancerProperties properties
    Sku sku

    LoadBalancer(AzureLoadBalancerDescription description) {
      apiVersion = "[variables('apiVersion')]"
      name = "[variables('loadBalancerName')]"
      type = "Microsoft.Network/loadBalancers"
      location = "[parameters('location')]"
      sku = new Sku("Standard")
      def currentTime = System.currentTimeMillis()
      tags = [:]
      tags.appName = description.appName
      tags.stack = description.stack
      tags.detail = description.detail
      tags.createdTime = currentTime.toString()
      if (description.cluster) tags.cluster = description.cluster

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
    ArrayList<BackEndAddressPool> backendAddressPools = []
    ArrayList<LoadBalancingRule> loadBalancingRules = []
    ArrayList<AzureProbe> probes = []

    LoadBalancerProperties(AzureLoadBalancerDescription description){
      frontEndIPConfigurations.add(new FrontEndIpConfiguration())
      backendAddressPools.add(new BackEndAddressPool())
      description.serverGroups?.each {
        backendAddressPools.add(new BackEndAddressPool(it))
      }
      description.loadBalancingRules?.each{
        it.persistence = description.sessionPersistence
        loadBalancingRules.add(new LoadBalancingRule(it))
      }
      description.probes?.each{ probes.add(new AzureProbe(it))}
    }
  }

  static class FrontEndIpConfiguration{
    String name
    FrontEndIpProperties properties

    FrontEndIpConfiguration()
    {
      name = "[variables('loadBalancerFrontEnd')]"
      properties = new FrontEndIpProperties("[variables('publicIPAddressID')]")
    }
  }

  static class BackEndAddressPool{
    String name

    BackEndAddressPool()
    {
      name = "[variables('loadBalancerBackEnd')]"
    }

    BackEndAddressPool(String name)
    {
      this.name = name
    }
  }

  static class FrontEndIpProperties{
    IdRef publicIPAddress

    FrontEndIpProperties(String id){
      publicIPAddress = new IdRef(id)
    }
  }

  static class Subnet{
    def name = '''[variables('subnetName')]'''
    def properties = new SubnetProperties()
  }

  static class SubnetProperties{
    def addressPrefix = '''[variables('subnetPrefix')]'''
  }

  static class NetworkInterfaceProperties{
    ArrayList<IPConfiguration> ipConfigurations = []

    public NetworkInterfaceProperties(){
      ipConfigurations.add( new IPConfiguration())
    }
  }

  static class IPConfiguration{
    String name = '''[variables('ipConfigName')]'''
    IPConfigurationProperties properties = new IPConfigurationProperties()
    ArrayList<IdRef> loadBalancerBackendAddressPools = []

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
    static enum LoadDistribution {
      Default,
      SourceIP,
      SourceIPProtocol
    }

    IdRef frontendIPConfiguration
    IdRef backendAddressPool
    String protocol
    Integer frontendPort
    Integer backendPort
    IdRef probe
    LoadDistribution loadDistribution

    LoadBalancingRuleProperties(AzureLoadBalancerDescription.AzureLoadBalancingRule rule){
      frontendIPConfiguration = new IdRef("[variables('frontEndIPConfig')]")
      backendAddressPool = new IdRef("[variables('backendPoolID')]")
      protocol = rule.protocol.toString().toLowerCase()
      frontendPort = rule.externalPort
      backendPort = rule.backendPort
      probe = new IdRef("[concat(variables('loadBalancerID'),'/probes/" + rule.probeName + "')]")
      switch(rule.persistence) {
        case "None":
          loadDistribution = LoadDistribution.Default
          break
        case "Client IP":
          loadDistribution = LoadDistribution.SourceIP
          break
        case "Client IP and protocol":
          loadDistribution = LoadDistribution.SourceIPProtocol
          break
      }
    }
  }
}
