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

import com.microsoft.azure.management.network.NetworkResourceProviderClient
import com.microsoft.azure.management.network.NetworkResourceProviderService
import com.microsoft.azure.management.network.models.AzureAsyncOperationResponse
import com.microsoft.azure.management.network.models.LoadBalancer
import com.microsoft.azure.management.network.models.VirtualNetwork
import com.microsoft.azure.utility.NetworkHelper
import com.microsoft.windowsazure.core.OperationResponse
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureVirtualNetworkDescription
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnetDescription
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.CompileStatic

@CompileStatic
class AzureNetworkClient extends AzureBaseClient {
  AzureNetworkClient(String subscriptionId) {
    super(subscriptionId)
  }

  /**
   * Retrieve a collection of all load balancer for a give set of credentials, regardless of resource group/region
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @return a Collection of objects which represent a Load Balancer in Azure
   */
  Collection<LoadBalancer> getLoadBalancersAll(AzureCredentials creds) {
    this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().listAll().getLoadBalancers()
  }

  /**
   * Retrieve a collection of all load balancers within a given resource group
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName name of the resource group where the load balancers were created
   * @return a Collection of objects which represent a Load Balancer in Azure
   */
  Collection<LoadBalancer> getLoadBalancersForResourceGroup(AzureCredentials creds, String resourceGroupName) {
    this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().list(resourceGroupName).getLoadBalancers()
  }

  /**
   * Retrieve the specified load balancer within a given azure credential, across all resource groups
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param loadBalancerName name of the load balancer in Azure
   * @return an object which represents a Load Balancer in Azure
   */
  LoadBalancer getLoadBalancer(AzureCredentials creds, String loadBalancerName) {
    findLoadBalancer(getLoadBalancersAll(creds), loadBalancerName)
  }

  /**
   * Retrieve the specified load balancer within a given resource group
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName name of the resource group where the load balancer was created
   * @param loadBalancerName name of the load balancer in Azure
   * @return an object which represents a Load Balancer in Azure
   */
  LoadBalancer getLoadBalancerInResourceGroup(AzureCredentials creds, String resourceGroupName, String loadBalancerName) {
    findLoadBalancer(getLoadBalancersForResourceGroup(creds, resourceGroupName), loadBalancerName)
  }

  /**
   * get the health state of a load balancer in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param loadBalancerName the name of the load balancer in Azure
   * @return A String representation of the current state of the given load balancer
   */
  String getLoadBalancerHealthState(AzureCredentials creds, String loadBalancerName) {
    getLoadBalancer(creds, loadBalancerName).getProvisioningState();
  }

  /**
   * Delete a load balancer in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName name of the resource group where the load balancer was created (see application name and region/location)
   * @param loadBalancerName name of the load balancer to delete
   * @return an OperationResponse object
   */
  OperationResponse deleteLoadBalancer(AzureCredentials creds, String resourceGroupName, String loadBalancerName) {
    this.getNetworkResourceProviderClient(creds).getLoadBalancersOperations().delete(resourceGroupName, loadBalancerName)
  }

  /**
   *
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName name of the resource group where the load balancer was created
   * @param virtualNetworkName name of the virtual network to create
   * @param region region to create the resource in
   */
  void createVirtualNetwork(AzureCredentials creds, String resourceGroupName, String virtualNetworkName, String region) {
    try {
      //Create the virtual network for the resource group
      AzureAsyncOperationResponse response = this.getNetworkResourceProviderClient(creds).
        getVirtualNetworksOperations().
        createOrUpdate(resourceGroupName, virtualNetworkName, new VirtualNetwork(region))
    }
    catch (e) {
      throw new RuntimeException("Unable to create Virtual network ${virtualNetworkName} in Resource Group ${resourceGroupName}", e)
    }
  }

  /**
   * Retrieve a collection of all subnets for a give set of credentials, regardless of resource group/region
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @return a Collection of objects which represent a Subnet in Azure
   */
  Collection<AzureSubnetDescription> getSubnetsAll(AzureCredentials creds) {
    def list = this.getNetworkResourceProviderClient(creds).getVirtualNetworksOperations().listAll().virtualNetworks

    def result = new ArrayList<AzureSubnetDescription>()

    for (VirtualNetwork item : list) {
      for (com.microsoft.azure.management.network.models.Subnet itemSubnet : item.subnets) {
        def subnetItem = new AzureSubnetDescription()
        subnetItem.name = itemSubnet.name
        subnetItem.region = item.location
        subnetItem.provisioningState = itemSubnet.provisioningState
        subnetItem.etag = itemSubnet.etag
        subnetItem.id = itemSubnet.id
        subnetItem.addressPrefix = itemSubnet.addressPrefix
        //subnetItem.ipConfigurations = itemSubnet.ipConfigurations
        subnetItem.networkSecurityGroup = itemSubnet.networkSecurityGroup.id
        subnetItem.routeTable = itemSubnet.routeTable.id
        result += subnetItem
      }
    }

    result
  }

  /**
   * Retrieve a collection of all virtual networks for a give set of credentials, regardless of resource group/region
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @return a Collection of objects which represent a Virtual Network in Azure
   */
  Collection<AzureVirtualNetworkDescription> getVirtualNetworksAll(AzureCredentials creds) {
    def list = this.getNetworkResourceProviderClient(creds).getVirtualNetworksOperations().listAll().virtualNetworks

    def result = new ArrayList<AzureVirtualNetworkDescription>()

    for (VirtualNetwork item : list) {
      def vnetItem = new AzureVirtualNetworkDescription()

      vnetItem.name = item.name
      vnetItem.location = item.location
      vnetItem.region = item.location
      vnetItem.addressSpace = item.addressSpace.addressPrefixes
      vnetItem.dhcpOptions = item.dhcpOptions.dnsServers
      vnetItem.provisioningState = item.provisioningState
      vnetItem.resourceGuid = item.resourceGuid

      def resultSubnet = new ArrayList<AzureSubnetDescription>()
      for (com.microsoft.azure.management.network.models.Subnet itemSubnet : item.subnets) {
        def subnetItem = new AzureSubnetDescription()
        subnetItem.name = itemSubnet.name
        subnetItem.region = item.location
        subnetItem.provisioningState = itemSubnet.provisioningState
        subnetItem.etag = itemSubnet.etag
        subnetItem.id = itemSubnet.id
        subnetItem.addressPrefix = itemSubnet.addressPrefix
        //subnetItem.ipConfigurations = itemSubnet.ipConfigurations
        subnetItem.networkSecurityGroup = itemSubnet.networkSecurityGroup.id
        subnetItem.routeTable = itemSubnet.routeTable.id
        resultSubnet += subnetItem
      }

      vnetItem.subnets = resultSubnet
      vnetItem.etag = item.etag
      vnetItem.id = item.id
      vnetItem.tags = item.tags
      vnetItem.type = item.type
      result += vnetItem
    }

    result
  }

  /**
   * get the NetworkResourceProviderClient which will be used for all interaction related to network resources in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @return an instance of the Azure NetworkResourceProviderClient
   */
  protected NetworkResourceProviderClient getNetworkResourceProviderClient(AzureCredentials creds) {
    NetworkResourceProviderService.create(this.buildConfiguration(creds))
  }

  /**
   * Find the load balancer by name
   * @param loadBalancers collection of load balancers to search in
   * @param loadBalancerName name of the load balancer to search for
   * @return an object which represents a load balancer in Azure
   */
  private static LoadBalancer findLoadBalancer(Collection<LoadBalancer> loadBalancers, String loadBalancerName) {
    loadBalancers.find { it.name == loadBalancerName }
  }
}
