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

import com.microsoft.azure.CloudException
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.management.network.ApplicationGatewaysOperations
import com.microsoft.azure.management.network.LoadBalancersOperations
import com.microsoft.azure.management.network.NetworkManagementClient
import com.microsoft.azure.management.network.NetworkManagementClientImpl
import com.microsoft.azure.management.network.NetworkSecurityGroupsOperations
import com.microsoft.azure.management.network.PublicIPAddressesOperations
import com.microsoft.azure.management.network.SubnetsOperations
import com.microsoft.azure.management.network.VirtualNetworksOperations
import com.microsoft.azure.management.network.models.AddressSpace
import com.microsoft.azure.management.network.models.ApplicationGatewayBackendAddressPool
import com.microsoft.azure.management.network.models.DhcpOptions
import com.microsoft.azure.management.network.models.NetworkSecurityGroup
import com.microsoft.azure.management.network.models.PublicIPAddress
import com.microsoft.azure.management.network.models.Subnet
import com.microsoft.azure.management.network.models.VirtualNetwork

import com.microsoft.rest.ServiceResponse
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureVirtualNetworkDescription
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnetDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureAppGatewayResourceTemplate
import groovy.util.logging.Slf4j
import okhttp3.logging.HttpLoggingInterceptor

@Slf4j
class AzureNetworkClient extends AzureBaseClient {

  private final NetworkManagementClient client

  AzureNetworkClient(String subscriptionId, ApplicationTokenCredentials credentials, String userAgentApplicationName) {
    super(subscriptionId, userAgentApplicationName)
    this.client = initializeClient(credentials)
  }

  @Lazy
  private LoadBalancersOperations loadBalancerOps = { client.getLoadBalancersOperations() }()

  @Lazy
  private ApplicationGatewaysOperations appGatewayOps = { client.getApplicationGatewaysOperations() }()

  @Lazy
  private VirtualNetworksOperations virtualNetworksOperations = { client.getVirtualNetworksOperations() }()

  @Lazy
  private SubnetsOperations subnetOperations = { client.getSubnetsOperations() }()

  @Lazy
  private NetworkSecurityGroupsOperations networkSecurityGroupOperations = { client.getNetworkSecurityGroupsOperations() }()

  @Lazy
  private PublicIPAddressesOperations publicIPAddressOperations = {client.getPublicIPAddressesOperations() }()


  /**
   * Retrieve a collection of all load balancer for a give set of credentials and the location
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Load Balancer in Azure
   */
  Collection<AzureLoadBalancerDescription> getLoadBalancersAll(String region) {
    def result = new ArrayList<AzureLoadBalancerDescription>()

    try {
      def loadBalancers = executeOp({loadBalancerOps.listAll()})?.body
      def currentTime = System.currentTimeMillis()
      loadBalancers?.each {item ->
        if (item.location == region) {
          try {
            def lbItem = AzureLoadBalancerDescription.build(item)
            lbItem.dnsName = getDnsNameForPublicIp(
              AzureUtilities.getResourceGroupNameFromResourceId(item.id),
              AzureUtilities.getNameFromResourceId(item.frontendIPConfigurations?.first()?.getPublicIPAddress()?.id)
            )
            lbItem.lastReadTime = currentTime
            result += lbItem
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the
            // load balancers
            log.error("Unable to process load balancer ${item.name}: ${re.message}")
          }
        }
      }
    } catch (Exception e) {
      log.error("getLoadBalancersAll -> Unexpected exception ", e)
    }

    result
  }

  /**
   * Retrieve an Azure load balancer for a give set of credentials, resource group and name
   * @param resourceGroupName the name of the resource group to look into
   * @param loadBalancerName the of the load balancer
   * @return a description object which represent a Load Balancer in Azure or null if Load Balancer could not be retrieved
   */
  AzureLoadBalancerDescription getLoadBalancer(String resourceGroupName, String loadBalancerName) {
    try {
      def currentTime = System.currentTimeMillis()
      def item = executeOp({loadBalancerOps.get(resourceGroupName, loadBalancerName, null)})?.body
      if (item) {
        def lbItem = AzureLoadBalancerDescription.build(item)
        lbItem.dnsName = getDnsNameForPublicIp(
          AzureUtilities.getResourceGroupNameFromResourceId(item.id),
          AzureUtilities.getNameFromResourceId(item.frontendIPConfigurations?.first()?.getPublicIPAddress()?.id)
        )
        lbItem.lastReadTime = currentTime
        return lbItem
      }
    } catch (CloudException e) {
      log.error("getLoadBalancer(${resourceGroupName},${loadBalancerName}) -> Cloud Exception ", e)
    }

    null
  }

  /**
   * Delete a load balancer in Azure
   * @param resourceGroupName name of the resource group where the load balancer was created (see application name and region/location)
   * @param loadBalancerName name of the load balancer to delete
   * @return a ServiceResponse object
   */
  ServiceResponse deleteLoadBalancer(String resourceGroupName, String loadBalancerName) {
    def loadBalancer = executeOp({loadBalancerOps.get(resourceGroupName, loadBalancerName, null)})?.body

    if (loadBalancer?.frontendIPConfigurations?.size() != 1) {
      throw new RuntimeException("Unexpected number of public IP addresses associated with the load balancer (should always be only one)!")
    }

    def publicIpAddressName = AzureUtilities.getNameFromResourceId(loadBalancer.frontendIPConfigurations.first().getPublicIPAddress().id)

    deleteAzureResource(
      loadBalancerOps.&delete,
      resourceGroupName,
      loadBalancerName,
      null,
      "Delete Load Balancer ${loadBalancerName}",
      "Failed to delete Load Balancer ${loadBalancerName} in ${resourceGroupName}"
    )

    // delete the public IP resource that was created and associated with the deleted load balancer
    deletePublicIp(resourceGroupName, publicIpAddressName)
  }

  /**
   * Delete a public IP resource in Azure
   * @param resourceGroupName name of the resource group where the public IP resource was created (see application name and region/location)
   * @param publicIpName name of the publicIp resource to delete
   * @return a ServiceResponse object
   */
  ServiceResponse deletePublicIp(String resourceGroupName, String publicIpName) {

    deleteAzureResource(
      publicIPAddressOperations.&delete,
      resourceGroupName,
      publicIpName,
      null,
      "Delete PublicIp ${publicIpName}",
      "Failed to delete PublicIp ${publicIpName} in ${resourceGroupName}"
    )
  }

  /**
   * It retrieves an Azure Application Gateway for a given set of credentials, resource group and name
   * @param resourceGroupName the name of the resource group to look into
   * @param appGatewayName the name of the application gateway
   * @return a description object which represents an application gateway in Azure or null if the application gateway resource could not be retrieved
   */
  AzureAppGatewayDescription getAppGateway(String resourceGroupName, String appGatewayName) {
    try {
      def currentTime = System.currentTimeMillis()
      def appGateway = executeOp({appGatewayOps.get(resourceGroupName, appGatewayName)})?.body
      if (appGateway) {
        def agItem = AzureAppGatewayDescription.getDescriptionForAppGateway(appGateway)
        agItem.dnsName = getDnsNameForPublicIp(
          AzureUtilities.getResourceGroupNameFromResourceId(appGateway.id),
          AzureUtilities.getNameFromResourceId(appGateway.frontendIPConfigurations?.first()?.getPublicIPAddress()?.id)
        )
        agItem.lastReadTime = currentTime
        return agItem
      }
    } catch (Exception e) {
      log.warn("getAppGateway(${resourceGroupName},${appGatewayName}) -> Exception ", e)
    }

    null
  }

  /**
   * It retrieves a collection of all application gateways for a given set of credentials and the location
   * @param region the location where to look for and retrieve all the application gateway resources
   * @return a Collection of objects which represent an Application Gateway in Azure
   */
  Collection<AzureAppGatewayDescription> getAppGatewaysAll(String region) {
    def result = []

    try {
      def currentTime = System.currentTimeMillis()
      def appGateways = executeOp({appGatewayOps.listAll()})?.body

      appGateways.each {item ->
        if (item.location == region) {
          try {
            def agItem = AzureAppGatewayDescription.getDescriptionForAppGateway(item)
            agItem.dnsName = getDnsNameForPublicIp(
              AzureUtilities.getResourceGroupNameFromResourceId(item.id),
              AzureUtilities.getNameFromResourceId(item.frontendIPConfigurations?.first()?.getPublicIPAddress()?.id)
            )
            agItem.lastReadTime = currentTime
            result << agItem
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the
            // load balancers
            log.error("Unable to process application gateway ${item.name}: ${re.message}")
          }
        }
      }
    } catch (Exception e) {
      log.error("getAppGatewaysAll -> Unexpected exception ", e)
    }

    result
  }

  /**
   * It deletes an Application Gateway resource in Azure
   * @param resourceGroupName name of the resource group where the Application Gateway resource was created (see application name and region/location)
   * @param appGatewayName name of the Application Gateway resource to delete
   * @return a ServiceResponse object or an Exception if we can't delete
   */
  ServiceResponse deleteAppGateway(String resourceGroupName, String appGatewayName) {
    ServiceResponse result
    def appGateway = executeOp({appGatewayOps.get(resourceGroupName, appGatewayName)})?.body

    if (appGateway?.tags?.cluster) {
      // The selected can not be deleted because there are active server groups associated with
      def errMsg = "Failed to delete ${appGatewayName}; the application gateway is still associated with server groups in ${appGateway.tags.cluster} cluster"
      log.error(errMsg)
      throw new RuntimeException(errMsg)
    }

    // TODO: retrieve private IP address name when support for it is added
    // First item in the application gateway frontend IP configurations is the public IP address we are loking for
    def publicIpAddressName = AzureUtilities.getNameFromResourceId(appGateway?.frontendIPConfigurations?.first()?.getPublicIPAddress()?.id)

    result = deleteAzureResource(
      appGatewayOps.&delete,
      resourceGroupName,
      appGatewayName,
      null,
      "Delete Application Gateway ${appGatewayName}",
      "Failed to delete Application Gateway ${appGatewayName} in ${resourceGroupName}"
    )

    // delete the public IP resource that was created and associated with the deleted load balancer
    if (publicIpAddressName) result = deletePublicIp(resourceGroupName, publicIpAddressName)

    result
  }

  /**
   * It creates the server group corresponding backend address pool entry in the selected application gateway
   *  This will be later used as a parameter in the create server group deployment template
   * @param resourceGroupName the name of the resource group to look into
   * @param appGatewayName the of the application gateway
   * @param serverGroupName the of the application gateway
   * @return a resource id for the backend address pool that got created or null/Runtime Exception if something went wrong
   */
  String createAppGatewayBAPforServerGroup(String resourceGroupName, String appGatewayName, String serverGroupName) {
    def appGateway = executeOp({appGatewayOps.get(resourceGroupName, appGatewayName)})?.body

    if (appGateway) {
      def agDescription = AzureAppGatewayDescription.getDescriptionForAppGateway(appGateway)
      def parsedName = Names.parseName(serverGroupName)

      if (!agDescription || (agDescription.cluster && agDescription.cluster != parsedName.cluster)) {
        // The selected server group must be in the same cluster and region (see resourceGroup) with the one already
        //   assigned for the selected application gateway.
        def errMsg = "Failed to associate ${serverGroupName} with ${appGatewayName}; expecting server group to be in ${agDescription.cluster} cluster"
        log.error(errMsg)
        throw new RuntimeException(errMsg)
      }

      // the application gateway must have an backend address pool list (even if it might be empty)
      if (!appGateway.backendAddressPools.find {it.name == serverGroupName}) {
        appGateway.backendAddressPools.add(new ApplicationGatewayBackendAddressPool(name: serverGroupName))
        if (agDescription.serverGroups) {
          agDescription.serverGroups << serverGroupName
        } else {
          agDescription.serverGroups = [serverGroupName]
        }
        appGateway.tags.cluster = parsedName.cluster
        // TODO: debug only; remove this as part of the cleanup
        appGateway.tags.serverGroups = agDescription.serverGroups.join(" ")
        log.info("Adding backend address pool to ${appGateway.name} for server group ${serverGroupName}")
        executeOp({appGatewayOps.createOrUpdate(resourceGroupName, appGatewayName, appGateway)})
        log.info("Backend address pool added")
      }

      return "${appGateway.id}/backendAddressPools/${serverGroupName}"
    }

    null
  }

  /**
   * It removes the server group corresponding backend address pool item from the selected application gateway (see disable/destroy server group op)
   * @param resourceGroupName the name of the resource group to look into
   * @param appGatewayName the of the application gateway
   * @param serverGroupName the of the application gateway
   * @return a resource id for the backend address pool that was removed or null/Runtime Exception if something went wrong
   */
  String removeAppGatewayBAPforServerGroup(String resourceGroupName, String appGatewayName, String serverGroupName) {
    def appGateway = executeOp({appGatewayOps.get(resourceGroupName, appGatewayName)})?.body

    if (appGateway) {
      def agDescription = AzureAppGatewayDescription.getDescriptionForAppGateway(appGateway)

      if (!agDescription) {
        def errMsg = "Failed to disassociate ${serverGroupName} from ${appGatewayName}; could not find ${appGatewayName}"
        log.error(errMsg)
        throw new RuntimeException(errMsg)
      }
      def agBAP = appGateway.backendAddressPools?.find { it.name == serverGroupName}
      if (agBAP) {
        appGateway.backendAddressPools.remove(agBAP)
        if (appGateway.backendAddressPools.size() == 1) {
          // There are no server groups assigned to ths application gateway; we can make it available now
          appGateway.tags.remove("cluster")
        }

        // TODO: debug only; remove this as part of the cleanup
        agDescription.serverGroups?.remove(serverGroupName)
        if (!agDescription.serverGroups || agDescription.serverGroups.isEmpty()) {
          appGateway.tags.remove("serverGroups")
        } else {
          appGateway.tags.remove("serverGroups")
          appGateway.tags.serverGroups = agDescription.serverGroups.join(" ")
        }

        executeOp({appGatewayOps.createOrUpdate(resourceGroupName, appGatewayName, appGateway)})
      }

      return "${appGateway.id}/backendAddressPools/${serverGroupName}"
    }

    null
  }

  /**
   * It enables a server group that is attached to an Application Gateway resource in Azure
   * @param resourceGroupName name of the resource group where the Application Gateway resource was created (see application name and region/location)
   * @param appGatewayName the of the application gateway
   * @param serverGroupName name of the server group to be enabled
   * @return a ServiceResponse object
   */
  ServiceResponse enableServerGroup(String resourceGroupName, String appGatewayName, String serverGroupName) {
    def appGateway = executeOp({appGatewayOps.get(resourceGroupName, appGatewayName)})?.body

    if (appGateway) {
      def agBAP = appGateway.backendAddressPools?.find { it.name == serverGroupName}
      if (!agBAP) {
        def errMsg = "Backend address pool ${serverGroupName} not found in ${appGatewayName}"
        log.error(errMsg)
        throw new RuntimeException(errMsg)
      }

      appGateway.requestRoutingRules.each {
        it.backendAddressPool.id = agBAP.id
      }

      // Store active server group in the tags map to ease debugging the operation; we could probably remove this later on
      appGateway.tags.trafficEnabledSG = serverGroupName

      return executeOp({appGatewayOps.createOrUpdate(resourceGroupName, appGatewayName, appGateway)})
    }

    null
  }

  /**
   * It disables a server group that is attached to an Application Gateway resource in Azure
   * @param resourceGroupName name of the resource group where the Application Gateway resource was created (see application name and region/location)
   * @param appGatewayName the of the application gateway
   * @param serverGroupName name of the server group to be disabled
   * @return a ServiceResponse object (null if no updates were performed)
   */
  ServiceResponse disableServerGroup(String resourceGroupName, String appGatewayName, String serverGroupName) {
    def appGateway = executeOp({appGatewayOps.get(resourceGroupName, appGatewayName)})?.body

    if (appGateway) {
      def defaultBAP = appGateway.backendAddressPools?.find { it.name == AzureAppGatewayResourceTemplate.defaultAppGatewayBeAddrPoolName }
      if (!defaultBAP) {
        def errMsg = "Backend address pool ${AzureAppGatewayResourceTemplate.defaultAppGatewayBeAddrPoolName} not found in ${appGatewayName}"
        log.error(errMsg)
        throw new RuntimeException(errMsg)
      }

      def agBAP = appGateway.backendAddressPools?.find { it.name == serverGroupName}
      if (!agBAP) {
        def errMsg = "Backend address pool ${serverGroupName} not found in ${appGatewayName}"
        log.error(errMsg)
        throw new RuntimeException(errMsg)
      }

      // Check if the current server group is the traffic enabled one and remove it (set default BAP as the active BAP)
      //  otherwise return (no updates are needed)
      def requestedRoutingRules = appGateway.requestRoutingRules?.findAll() {
        it.backendAddressPool.id == agBAP.id
      }

      if (requestedRoutingRules) {
        requestedRoutingRules.each {
          it.backendAddressPool.id = defaultBAP.id
        }

        // Clear active server group (if any) from the tags map to ease debugging the operation; we will clean this later
        appGateway.tags.remove("trafficEnabledSG")

        return executeOp({ appGatewayOps.createOrUpdate(resourceGroupName, appGatewayName, appGateway) })
      }
    }

    null
  }

  /**
   * Checks if a server group that is attached to an Application Gateway resource in Azure is set to receive traffic
   * @param resourceGroupName name of the resource group where the Application Gateway resource was created (see application name and region/location)
   * @param appGatewayName the of the application gateway
   * @param serverGroupName name of the server group to be disabled
   * @return true or false
   */
  Boolean isServerGroupDisabled(String resourceGroupName, String appGatewayName, String serverGroupName) {
    def appGateway = executeOp({appGatewayOps.get(resourceGroupName, appGatewayName)})?.body

    if (appGateway) {
      def agBAP = appGateway.backendAddressPools?.find { it.name == serverGroupName }
      if (agBAP) {
        // Check if the current server group is the traffic enabled one
        def requestedRoutingRule = appGateway.requestRoutingRules?.find() {
          it.backendAddressPool.id == agBAP.id
        }

        if (requestedRoutingRule) {
          return false
        }
      }
    }

    true
  }

  /**
   * Delete a network security group in Azure
   * @param resourceGroupName name of the resource group where the security group was created (see application name and region/location)
   * @param securityGroupName name of the Azure network security group to delete
   * @return a ServiceResponse object
   */
  ServiceResponse deleteSecurityGroup(String resourceGroupName, String securityGroupName) {

    deleteAzureResource(
      networkSecurityGroupOperations.&delete,
      resourceGroupName,
      securityGroupName,
      null,
      "Delete Security Group ${securityGroupName}",
      "Failed to delete Security Group ${securityGroupName} in ${resourceGroupName}"
    )
  }

  /**
   * Create an Azure virtual network resource
   * @param resourceGroupName name of the resource group where the load balancer was created
   * @param virtualNetworkName name of the virtual network to create
   * @param region region to create the resource in
   */
  void createVirtualNetwork(String resourceGroupName, String virtualNetworkName, String region, String addressPrefix = AzureUtilities.VNET_DEFAULT_ADDRESS_PREFIX) {
    try {
      List<Subnet> subnets = []

      // Define address space
      List<String> addressPrefixes = []
      addressPrefixes.add(addressPrefix)
      AddressSpace addressSpace = new AddressSpace()
      addressSpace.setAddressPrefixes(addressPrefixes)

      // Define DHCP Options
      DhcpOptions dhcpOptions = new DhcpOptions()
      dhcpOptions.dnsServers = []

      VirtualNetwork virtualNetwork = new VirtualNetwork()
      virtualNetwork.setLocation(region)
      virtualNetwork.setDhcpOptions(dhcpOptions)
      virtualNetwork.setSubnets(subnets)
      virtualNetwork.setAddressSpace(addressSpace)

      //Create the virtual network for the resource group
      virtualNetworksOperations.createOrUpdate(resourceGroupName, virtualNetworkName, virtualNetwork)
    }
    catch (e) {
      throw new RuntimeException("Unable to create Virtual network ${virtualNetworkName} in Resource Group ${resourceGroupName}", e)
    }
  }

  /**
   * Create a new subnet in the virtual network specified
   * @param resourceGroupName Resource Group in Azure where vNet and Subnet will exist
   * @param virtualNetworkName Virtual Network to create the subnet in
   * @param subnetName Name of subnet to create
   * @param addressPrefix - Address Prefix to use for Subnet defaults to 10.0.0.0/24
   * @throws RuntimeException Throws RuntimeException if operation response indicates failure
   * @returns Resource ID of subnet created
   */
  String createSubnet(String resourceGroupName, String virtualNetworkName, String subnetName, String addressPrefix, String securityGroupName) {
    Subnet subnet = new Subnet()
    subnet.setAddressPrefix(addressPrefix)

    if (securityGroupName) {
      addSecurityGroupToSubnet(resourceGroupName, securityGroupName, subnet)
    }

    //This will throw an exception if the it fails. If it returns then the call was successful
    //Log the error Let it bubble up to the caller to handle as they see fit
    try {
      def op = subnetOperations.createOrUpdate(resourceGroupName, virtualNetworkName, subnetName, subnet)

      // Return the resource Id
      op?.body?.id

    } catch (Exception e) {
      // Add something to the log to show that the subnet creation failed then rethrow the exception
      log.error("Unable to create subnet ${subnetName} in Resource Group ${resourceGroupName}")
      throw e
    }
  }

  /**
   * Delete a subnet in the virtual network specified
   * @param resourceGroupName Resource Group in Azure where vNet and Subnet will exist
   * @param virtualNetworkName Virtual Network to create the subnet in
   * @param subnetName Name of subnet to create
   * @throws RuntimeException Throws RuntimeException if operation response indicates failure
   * @return a ServiceResponse object
   */
  ServiceResponse<Void> deleteSubnet(String resourceGroupName, String virtualNetworkName, String subnetName) {

    deleteAzureResource(
      subnetOperations.&delete,
      resourceGroupName,
      subnetName,
      virtualNetworkName,
      "Delete subnet ${subnetName}",
      "Failed to delete subnet ${subnetName} in ${resourceGroupName}"
    )
  }

  private void addSecurityGroupToSubnet(String resourceGroupName, String securityGroupName, Subnet subnet) {
    def securityGroup = executeOp({networkSecurityGroupOperations.get(resourceGroupName, securityGroupName, null)})?.body
    subnet.setNetworkSecurityGroup(securityGroup)
  }

  /**
   * Retrieve a collection of all network security groups for a give set of credentials and the location
   * @param region the location of the network security group
   * @return a Collection of objects which represent a Network Security Group in Azure
   */
  Collection<AzureSecurityGroupDescription> getNetworkSecurityGroupsAll(String region) {
    def result = new ArrayList<AzureSecurityGroupDescription>()

    try {
      def securityGroups = executeOp({networkSecurityGroupOperations.listAll()})?.body
      def currentTime = System.currentTimeMillis()
      securityGroups?.each { item ->
        if (item.location == region) {
          try {
            def sgItem = getAzureSecurityGroupDescription(item)
            sgItem.lastReadTime = currentTime
            result += sgItem
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the
            // NSGs
            log.error("Unable to process network security group ${item.name}: ${re.message}")
          }
        }
      }
    } catch (Exception e) {
      log.error("getNetworkSecurityGroupsAll -> Unexpected exception ", e)
    }

    result
  }

  /**
   * Retrieve an Azure network security group for a give set of credentials, resource group and network security group name
   * @param resourceGroupName name of the resource group where the security group was created (see application name and region/location)
   * @param securityGroupName name of the Azure network security group to be retrieved
   * @return a description object which represent a Network Security Group in Azure
   */
  AzureSecurityGroupDescription getNetworkSecurityGroup(String resourceGroupName, String securityGroupName) {
    try {
      def securityGroup = executeOp({networkSecurityGroupOperations.get(resourceGroupName, securityGroupName, null)})?.body
      def currentTime = System.currentTimeMillis()
      def sgItem = getAzureSecurityGroupDescription(securityGroup)
      sgItem.lastReadTime = currentTime

      return sgItem
    } catch (Exception e) {
      log.error("getNetworkSecurityGroupsAll -> Unexpected exception ", e)
    }

    null
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
    sgItem.resourceId = item.id
    sgItem.tags = item.tags
    def parsedName = Names.parseName(item.name)
    sgItem.stack = item.tags?.stack ?: parsedName.stack
    sgItem.detail = item.tags?.detail ?: parsedName.detail
    sgItem.appName = item.tags?.appName ?: parsedName.app
    sgItem.createdTime = item.tags?.createdTime?.toLong()
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
   * Retrieve a collection of all subnets for a give set of credentials and the location
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Subnet in Azure
   */
  Collection<AzureSubnetDescription> getSubnetsInRegion(String region) {
    def result = new ArrayList<AzureSubnetDescription>()

    try {
      def vnets = executeOp({virtualNetworksOperations.listAll()})?.body
      def currentTime = System.currentTimeMillis()
      vnets?.each { item->
        if (item.location == region) {
          try {
            AzureSubnetDescription.getSubnetsForVirtualNetwork(item).each { AzureSubnetDescription subnet ->
              subnet.lastReadTime = currentTime
              result += subnet
            }
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the subnets
            log.error("Unable to process subnets for virtual network ${item.name}", re)
          }
        }
      }
    } catch (Exception e) {
      log.error("getSubnetsAll -> Unexpected exception ", e)
    }

    result
  }

  /**
   * Gets a virtual network object instance by name, or null if the virtual network does not exist
   * @param resourceGroupName name of the resource group to look in for a virtual network
   * @param virtualNetworkName name of the virtual network to get
   * @return virtual network instance, or null if it does not exist
   */
  VirtualNetwork getVirtualNetwork(String resourceGroupName, String virtualNetworkName) {
    executeOp({virtualNetworksOperations.get(resourceGroupName, virtualNetworkName, null)})?.body
  }

  /**
   * Retrieve a collection of all virtual networks for a give set of credentials and the location
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Virtual Network in Azure
   */
  Collection<AzureVirtualNetworkDescription> getVirtualNetworksAll(String region){
    def result = new ArrayList<AzureVirtualNetworkDescription>()

    try {
      def vnetList = executeOp({virtualNetworksOperations.listAll()})?.body
      def currentTime = System.currentTimeMillis()
      vnetList?.each { item ->
        if (item.location == region) {
          try {
            if (item?.addressSpace?.addressPrefixes?.size() != 1) {
              log.warn("Virtual Network found with ${item?.addressSpace?.addressPrefixes?.size()} address spaces; expected: 1")
            }

            def vnet = AzureVirtualNetworkDescription.getDescriptionForVirtualNetwork(item)
            vnet.subnets = AzureSubnetDescription.getSubnetsForVirtualNetwork(item)

            def appGateways = executeOp({appGatewayOps.listAll()})?.body
            AzureSubnetDescription.getAppGatewaysConnectedResources(vnet, appGateways.findAll {it.location == region})

            vnet.lastReadTime = currentTime
            result += vnet
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the
            // virtual networks
            log.error("Unable to process virtual network ${item.name}", re)
          }
        }
      }
    } catch (Exception e) {
      log.error("getVirtualNetworksAll -> Unexpected exception ", e)
    }

    result
  }

  /**
   * get the dns name associated with a resource via the Public Ip dependency
   * @param resourceGroupName name of the resource group where the application gateway was created (see application name and region/location)
   * @param publicIpName the name of the public IP resource
   * @return the dns name of the Public IP or "dns-not-found"
   */
  String getDnsNameForPublicIp(String resourceGroupName, String publicIpName) {
    String dnsName = "dns-not-found"

    try {
      PublicIPAddress publicIp = publicIpName ?
        executeOp({publicIPAddressOperations.get(
          resourceGroupName,
          publicIpName, null)}
        )?.body : null
      if (publicIp?.dnsSettings?.fqdn) dnsName = publicIp.dnsSettings.fqdn
    } catch (Exception e) {
      log.error("getDnsNameForPublicIp -> Unexpected exception ", e)
    }

    dnsName
  }

  private NetworkManagementClient initializeClient(ApplicationTokenCredentials tokenCredentials) {
    NetworkManagementClient networkManagementClient = new NetworkManagementClientImpl(buildBaseUrl(tokenCredentials), tokenCredentials)
    networkManagementClient.setSubscriptionId(this.subscriptionId)
    networkManagementClient.setLogLevel(HttpLoggingInterceptor.Level.NONE)

    setUserAgent(networkManagementClient, userAgentApplicationName, true)

    networkManagementClient
  }

  /***
   * The namespace for the Azure Resource Provider
   * @return namespace of the resource provider
   */
  @Override
  String getProviderNamespace() {
    "Microsoft.Network"
  }

}
