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

package com.netflix.spinnaker.clouddriver.azure.client

import com.microsoft.azure.management.network.models.AddressSpace
import com.microsoft.azure.management.network.models.LoadBalancer
import com.microsoft.azure.management.network.models.NetworkSecurityGroup
import com.microsoft.azure.management.network.models.PublicIpAddress
import com.microsoft.azure.management.network.models.Subnet
import com.microsoft.azure.management.network.models.VirtualNetwork
import com.microsoft.azure.management.network.NetworkResourceProviderClient
import com.microsoft.azure.management.network.NetworkResourceProviderService
import com.microsoft.windowsazure.core.OperationResponse
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureVirtualNetworkDescription
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnetDescription
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class AzureNetworkClient extends AzureBaseClient {
  AzureNetworkClient(String subscriptionId) {
    super(subscriptionId)
  }

  /**
   * Retrieve a collection of all load balancer for a give set of credentials and the location
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Load Balancer in Azure
   */
  Collection<AzureLoadBalancerDescription> getLoadBalancersAll(AzureCredentials creds, String region) {
    def result = new ArrayList<AzureLoadBalancerDescription>()

    try {
      this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().listAll().getLoadBalancers().each {item ->
        if (item.location == region) {
          def lbItem = getDescriptionForLoadBalancer(item)
          lbItem.appName = AzureUtilities.getAppNameFromResourceId(item.id)
          lbItem.tags = item.tags
          lbItem.dnsName = getDnsNameForLoadBalancer(creds, AzureUtilities.getResourceGroupNameFromResourceId(item.id), item.name)
          result += lbItem
        }
      }
    } catch (Exception e) {
      log.info("getLoadBalancersAll -> Unexpected exception " + e.toString())
    }

    result
  }

  private static AzureLoadBalancerDescription getDescriptionForLoadBalancer(LoadBalancer azureLoadBalancer) {
    AzureLoadBalancerDescription description = new AzureLoadBalancerDescription(loadBalancerName: azureLoadBalancer.name)
    description.stack = azureLoadBalancer.tags["stack"]
    description.detail = azureLoadBalancer.tags["detail"]
    description.region = azureLoadBalancer.location

    for (def rule : azureLoadBalancer.loadBalancingRules) {
      def r = new AzureLoadBalancerDescription.AzureLoadBalancingRule(ruleName: rule.name)
      r.externalPort = rule.frontendPort
      r.backendPort = rule.backendPort
      r.probeName = AzureUtilities.getNameFromResourceId(rule.probe.id)
      r.persistence = rule.loadDistribution;
      r.idleTimeout = rule.idleTimeoutInMinutes;

      if (rule.protocol.toLowerCase() == "udp") {
        r.protocol = AzureLoadBalancerDescription.AzureLoadBalancingRule.AzureLoadBalancingRulesType.UDP
      } else {
        r.protocol = AzureLoadBalancerDescription.AzureLoadBalancingRule.AzureLoadBalancingRulesType.TCP
      }
      description.loadBalancingRules.add(r)
    }

    // Add the probes
    for (def probe : azureLoadBalancer.probes) {
      def p = new AzureLoadBalancerDescription.AzureLoadBalancerProbe()
      p.probeName = probe.name
      p.probeInterval = probe.intervalInSeconds
      p.probePath = probe.requestPath
      p.probePort = probe.port
      p.unhealthyThreshold = probe.numberOfProbes
      if (probe.protocol.toLowerCase() == "tcp") {
        p.probeProtocol = AzureLoadBalancerDescription.AzureLoadBalancerProbe.AzureLoadBalancerProbesType.TCP
      } else {
        p.probeProtocol = AzureLoadBalancerDescription.AzureLoadBalancerProbe.AzureLoadBalancerProbesType.HTTP
      }
      description.probes.add(p)
    }

    for (def natRule : azureLoadBalancer.inboundNatRules) {
      def n = new AzureLoadBalancerDescription.AzureLoadBalancerInboundNATRule(ruleName: natRule.name)
      description.inboundNATRules.add(n)
    }

    description
  }

  /**
   * Delete a load balancer in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName name of the resource group where the load balancer was created (see application name and region/location)
   * @param loadBalancerName name of the load balancer to delete
   * @return an OperationResponse object
   */
  OperationResponse deleteLoadBalancer(AzureCredentials creds, String resourceGroupName, String loadBalancerName) {
    def loadBalancer = getNetworkResourceProviderClient(creds).getLoadBalancersOperations().get(resourceGroupName, loadBalancerName).getLoadBalancer()

    if (loadBalancer.frontendIpConfigurations.size() != 1) {
      throw new RuntimeException("Unexpected number of public IP addresses associated with the load balancer (should be only one)!")
    }

    def publicIpAddressName = AzureUtilities.getResourceNameFromID(loadBalancer.frontendIpConfigurations.first().getPublicIpAddress().id)
    this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().delete(resourceGroupName, loadBalancerName)

    this.getNetworkResourceProviderClient(creds).getPublicIpAddressesOperations().delete(resourceGroupName, publicIpAddressName)
  }

  /**
   * Delete a network security group in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName name of the resource group where the load balancer was created (see application name and region/location)
   * @param securityGroupName name of the Azure network security group to delete
   * @return an OperationResponse object
   */
  OperationResponse deleteSecurityGroup(AzureCredentials creds, String resourceGroupName, String securityGroupName) {
    this.getNetworkResourceProviderClient(creds).getNetworkSecurityGroupsOperations().delete(resourceGroupName, securityGroupName)
  }

  /**
   *
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName name of the resource group where the load balancer was created
   * @param virtualNetworkName name of the virtual network to create
   * @param region region to create the resource in
   */
  void createVirtualNetwork(AzureCredentials creds, String resourceGroupName, String virtualNetworkName, String region, String addressPrefix = "10.0.0.0/16") {
    try {

      def virtualNetwork = new VirtualNetwork(region)
      AddressSpace addressSpace = new AddressSpace()
      addressSpace.addressPrefixes.add(addressPrefix)
      virtualNetwork.setAddressSpace(addressSpace)

      //Create the virtual network for the resource group
      this.getNetworkResourceProviderClient(creds).
        getVirtualNetworksOperations().
        createOrUpdate(resourceGroupName, virtualNetworkName, virtualNetwork)
    }
    catch (e) {
      throw new RuntimeException("Unable to create Virtual network ${virtualNetworkName} in Resource Group ${resourceGroupName}", e)
    }
  }

  void createSubnet(AzureCredentials creds, String resourceGroupName, String virtualNetworkName, String subnetName, String addressPrefix = '10.0.0.0/24') {
    try {
      def subnet = new Subnet(addressPrefix)
      this.getNetworkResourceProviderClient(creds).
        getSubnetsOperations().
        createOrUpdate(resourceGroupName, virtualNetworkName, subnetName, subnet)
      // TODO can we return the ID of the resulting subnet somehow?
    }
    catch (e) {
      throw new RuntimeException("Unable to create subnet ${subnetName} in Resource Group ${resourceGroupName}", e)
    }
  }

  /**
   * Retrieve a collection of all network security groups for a give set of credentials and the location
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Network Security Group in Azure
   */
  Collection<AzureSecurityGroupDescription> getNetworkSecurityGroupsAll(AzureCredentials creds, String region) {
    def result = new ArrayList<AzureSecurityGroupDescription>()

    try {
      this.getNetworkResourceProviderClient(creds).getNetworkSecurityGroupsOperations().listAll().networkSecurityGroups.each { item ->
        if (item.location == region) {
          result += getAzureSecurityGroupDescription(item)
        }
      }
    } catch (Exception e) {
      log.info("getNetworkSecurityGroupsAll -> Unexpected exception " + e.toString())
    }

    result
  }

  private static AzureSecurityGroupDescription getAzureSecurityGroupDescription(NetworkSecurityGroup item) {
    def sgItem = new AzureSecurityGroupDescription()

    sgItem.name = item.name
    sgItem.id = item.name
    sgItem.location = item.location
    sgItem.region = item.location
    sgItem.cloudProvider = "azure"
    sgItem.provisioningState = item.provisioningState
    sgItem.resourceGuid = item.resourceGuid
    sgItem.etag = item.etag
    sgItem.resourceId = item.id
    sgItem.tags = item.tags
    sgItem.type = item.type
    sgItem.securityRules = new ArrayList<AzureSecurityGroupDescription.AzureSGRule>()
    item.securityRules?.each {rule -> sgItem.securityRules += new AzureSecurityGroupDescription.AzureSGRule(
      resourceId: rule.id,
      id: rule.name,
      name: rule.name,
      access: rule.access,
      priority: rule.priority,
      protocol: rule.protocol,
      direction: rule.direction,
      destinationAddressPrefix: rule.destinationAddressPrefix,
      destinationPortRange: rule.destinationPortRange,
      sourceAddressPrefix: rule.sourceAddressPrefix,
      sourcePortRange: rule.sourcePortRange) }
    sgItem.subnets = new ArrayList<String>()
    item.subnets?.each { sgItem.subnets += AzureUtilities.getNameFromResourceId(it.id) }
    sgItem.networkInterfaces = new ArrayList<String>()
    item.networkInterfaces?.each { sgItem.networkInterfaces += it.id }

    sgItem
  }

  /**
   * Retrieve a collection of subnet description objects for a given Azure VirtualNetwork object
   * @param vnet the Azure VirtualNetwork
   * @return a Collection of AzureSubnetDescription objects which represent a Subnet in Azure
   */
  static Collection<AzureSubnetDescription> getSubnetForVirtualNetwork(VirtualNetwork vnet) {
    def result = new ArrayList<AzureSubnetDescription>()

    vnet.subnets?.each { itemSubnet ->
      def subnetItem = new AzureSubnetDescription()
      subnetItem.name = itemSubnet.name
      subnetItem.region = vnet.location
      subnetItem.cloudProvider = "azure"
      subnetItem.vnet = vnet.name
      subnetItem.etag = itemSubnet.etag
      subnetItem.resourceId = itemSubnet.id
      subnetItem.id = itemSubnet.name
      subnetItem.addressPrefix = itemSubnet.addressPrefix
      itemSubnet.ipConfigurations.each {resourceId -> subnetItem.ipConfigurations += resourceId.id}
      subnetItem.networkSecurityGroup = itemSubnet.networkSecurityGroup?.id
      subnetItem.routeTable = itemSubnet.routeTable?.id
      result += subnetItem
    }

    result
  }

  /**
   * Retrieve a collection of all subnets for a give set of credentials and the location
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Subnet in Azure
   */
  Collection<AzureSubnetDescription> getSubnetsInRegion(AzureCredentials creds, String region) {
    def result = new ArrayList<AzureSubnetDescription>()

    try {
      this.getNetworkResourceProviderClient(creds).getVirtualNetworksOperations().listAll().virtualNetworks.each { item->
        if (item.location == region) {
          getSubnetForVirtualNetwork(item).each { AzureSubnetDescription subnet -> result += subnet }
        }
      }
    } catch (Exception e) {
      log.info("getSubnetsAll -> Unexpected exception " + e.toString())
    }

    result
  }

  /**
   * Retrieve a collection of all subnets for a give set of credentials, regardless of region, optionally
   * filtered for a given resource group
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName specify the resource group that is used to filter subnets; only
   *
   * @return a Collection of objects which represent a Subnet in Azure
   */
  Collection<AzureSubnetDescription> getSubnetsInResourceGroup(AzureCredentials creds, String resourceGroupName) {

    def result = new ArrayList<AzureSubnetDescription>()

    try {
      this.getNetworkResourceProviderClient(creds).getVirtualNetworksOperations().list(resourceGroupName).virtualNetworks.each { item->
        getSubnetForVirtualNetwork(item).each { AzureSubnetDescription subnet -> result += subnet }
      }
    } catch (Exception e) {
      log.info("getSubnetsAll -> Unexpected exception " + e.toString())
    }

    result
  }

  /**
   * Retrieves a particular subnet from a given resource group based on its name
   * @param creds
   * @param resourceGroupName
   * @param subnetName
     * @return an AzureSubnetDescription instance containing details about the given subnet
     */
  AzureSubnetDescription getSubnet(AzureCredentials creds, String resourceGroupName, String subnetName) {
    getSubnetsInResourceGroup(creds, resourceGroupName).find {it.name == subnetName}
  }

  /**
   * Gets a virtual network object instance by name, or null if the virtual network does not exist
   * @param creds the credentials to use when communicating with Azure subscription(s)
   * @param resourceGroupName name of the resource group to look in for a virtual network
   * @param virtualNetworkName name of the virtual network to get
   * @return virtual network instance, or null if it does not exist
   */
  VirtualNetwork getVirtualNetwork(AzureCredentials creds, String resourceGroupName, String virtualNetworkName) {
    this.getNetworkResourceProviderClient(creds).
      getVirtualNetworksOperations().
      get(resourceGroupName, virtualNetworkName).
      getVirtualNetwork()
  }

  /**
   * Retrieve a collection of all virtual networks for a give set of credentials and the location
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Virtual Network in Azure
   */
  Collection<AzureVirtualNetworkDescription> getVirtualNetworksAll(AzureCredentials creds, String region) {
    def result = new ArrayList<AzureVirtualNetworkDescription>()

    try {
      this.getNetworkResourceProviderClient(creds).getVirtualNetworksOperations().listAll().virtualNetworks.each { item ->
        if (item.location == region) {
          result += getAzureVirtualNetworkDescription(item)
        }
      }
    } catch (Exception e) {
      log.info("getVirtualNetworksAll -> Unexpected exception " + e.toString())
    }

    result
  }

  private static AzureVirtualNetworkDescription getAzureVirtualNetworkDescription(VirtualNetwork vnet) {
    def azureVirtualNetworkDescription = new AzureVirtualNetworkDescription()
    def subnets = getSubnetForVirtualNetwork(vnet)

    azureVirtualNetworkDescription.name = vnet.name
    azureVirtualNetworkDescription.location = vnet.location
    azureVirtualNetworkDescription.region = vnet.location
    azureVirtualNetworkDescription.addressSpace = vnet.addressSpace?.addressPrefixes
    azureVirtualNetworkDescription.dhcpOptions = vnet.dhcpOptions?.dnsServers
    azureVirtualNetworkDescription.provisioningState = vnet.provisioningState
    azureVirtualNetworkDescription.resourceGuid = vnet.resourceGuid
    azureVirtualNetworkDescription.subnets = subnets?.toList()
    azureVirtualNetworkDescription.etag = vnet.etag
    azureVirtualNetworkDescription.resourceId = vnet.id
    azureVirtualNetworkDescription.id = vnet.name
    azureVirtualNetworkDescription.tags = vnet.tags
    azureVirtualNetworkDescription.type = vnet.type

    azureVirtualNetworkDescription
  }

  /**
   * get the dns name associated with a load balancer in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName name of the resource group where the load balancer was created (see application name and region/location)
   * @param loadBalancerName the name of the load balancer in Azure
   * @return the dns name of the given load balancer
   */
  String getDnsNameForLoadBalancer(AzureCredentials creds, String resourceGroupName, String loadBalancerName) {
    String dnsName = "none"

    try {
      def loadBalancer = this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().get(resourceGroupName, loadBalancerName).getLoadBalancer()
      if (loadBalancer.frontendIpConfigurations) {
        if (loadBalancer.frontendIpConfigurations.size() != 1) {
          log.info("getDnsNameForLoadBalancer -> Unexpected number of public IP addresses associated with the load balancer (should be only one)!")
        }

        def publicIpResource = loadBalancer.frontendIpConfigurations.first()?.getPublicIpAddress()?.id
        PublicIpAddress publicIp = publicIpResource ? this.getNetworkResourceProviderClient(creds).getPublicIpAddressesOperations().get(resourceGroupName, AzureUtilities.getNameFromResourceId(publicIpResource))?.publicIpAddress : null
        dnsName = publicIp ? publicIp.dnsSettings?.fqdn : "none"
      }
    } catch (Exception e) {
      log.info("getDnsNameForLoadBalancer -> Unexpected exception " + e.toString())
    }

    dnsName
  }

  /**
   * get the NetworkResourceProviderClient which will be used for all interaction related to network resources in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @return an instance of the Azure NetworkResourceProviderClient
   */
  protected NetworkResourceProviderClient getNetworkResourceProviderClient(AzureCredentials creds) {
    NetworkResourceProviderService.create(this.buildConfiguration(creds))
  }

}
