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

import com.azure.core.credential.TokenCredential
import com.azure.core.http.rest.Response
import com.azure.core.management.SubResource
import com.azure.core.management.exception.ManagementException
import com.azure.core.management.profile.AzureProfile
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSet
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetIpConfiguration
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetVMs
import com.azure.resourcemanager.network.fluent.models.NetworkSecurityGroupInner
import com.azure.resourcemanager.network.models.LoadBalancer
import com.azure.resourcemanager.network.models.Network
import com.azure.resourcemanager.network.models.PublicIpAddress
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureVirtualNetworkDescription
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnetDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureAppGatewayResourceTemplate
import com.netflix.spinnaker.clouddriver.azure.templates.AzureLoadBalancerResourceTemplate
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j


@CompileStatic
@Slf4j
class AzureNetworkClient extends AzureBaseClient {
  AzureNetworkClient(String subscriptionId, TokenCredential credentials, AzureProfile azureProfile) {
    super(subscriptionId, azureProfile, credentials)
  }

  /**
   * Retrieve a collection of all load balancer for a give set of credentials and the location
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Load Balancer in Azure
   */
  Collection<AzureLoadBalancerDescription> getLoadBalancersAll(String region) {
    def result = new ArrayList<AzureLoadBalancerDescription>()

    try {
      def loadBalancers = executeOp({
        azure.loadBalancers().list().asList()
      })
      def currentTime = System.currentTimeMillis()
      loadBalancers?.each { item ->
        if (item.innerModel().location() == region) {
          try {
            def lbItem = AzureLoadBalancerDescription.build(item.innerModel())
            if (item.publicIpAddressIds() && !item.publicIpAddressIds().isEmpty()) {
              lbItem.dnsName = getDnsNameForPublicIp(
                AzureUtilities.getResourceGroupNameFromResourceId(item.id()),
                AzureUtilities.getNameFromResourceId(item.publicIpAddressIds()?.first())
              )
            }
            lbItem.lastReadTime = currentTime
            result += lbItem
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the
            // load balancers
            log.error("Unable to process load balancer ${item.name()}: ${re.message}")
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
      def item = executeOp({
        azure.loadBalancers().getByResourceGroup(resourceGroupName, loadBalancerName)
      })
      if (item) {
        def lbItem = AzureLoadBalancerDescription.build(item.innerModel())
        lbItem.dnsName = getDnsNameForPublicIp(
          AzureUtilities.getResourceGroupNameFromResourceId(item.id()),
          AzureUtilities.getNameFromResourceId(item.publicIpAddressIds()?.first())
        )
        lbItem.lastReadTime = currentTime
        return lbItem
      }
    } catch (ManagementException e) {
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
  Response deleteLoadBalancer(String resourceGroupName, String loadBalancerName) {
    def loadBalancer = azure.loadBalancers().getByResourceGroup(resourceGroupName, loadBalancerName)

    if (loadBalancer?.publicIpAddressIds()?.size() != 1) {
      throw new RuntimeException("Unexpected number of public IP addresses associated with the load balancer (should always be only one)!")
    }

    def publicIpAddressName = AzureUtilities.getNameFromResourceId(loadBalancer.publicIpAddressIds()?.first())

    deleteAzureResource(
      azure.loadBalancers().&deleteByResourceGroup,
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
  Response deletePublicIp(String resourceGroupName, String publicIpName) {

    deleteAzureResource(
      azure.publicIpAddresses().&deleteByResourceGroup,
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
      def appGateway = executeOp({
        azure.applicationGateways().getByResourceGroup(resourceGroupName, appGatewayName)
      })
      if (appGateway) {
        def agItem = AzureAppGatewayDescription.getDescriptionForAppGateway(appGateway.innerModel())
        agItem.dnsName = getDnsNameForPublicIp(
          AzureUtilities.getResourceGroupNameFromResourceId(appGateway.id()),
          AzureUtilities.getNameFromResourceId(appGateway.defaultPublicFrontend().publicIpAddressId())
        )
        agItem.lastReadTime = currentTime
        return agItem
      }
    } catch (Exception e) {
      if (AzureBaseClient.resourceNotFound(e)) {
        log.warn("getAppGateway(${resourceGroupName},${appGatewayName}) -> not found", e)
        return null
      }
      log.error("getAppGateway(${resourceGroupName},${appGatewayName}) failed", e)
      throw new RuntimeException("getAppGateway(${resourceGroupName},${appGatewayName}) failed: ${e.message}", e)
    }
  }

  /**
   * It retrieves a collection of all application gateways for a given set of credentials and the location
   * @param region the location where to look for and retrieve all the application gateway resources
   * @return a Collection of objects which represent an Application Gateway in Azure
   */
  Collection<AzureAppGatewayDescription> getAppGatewaysAll(String region) {
    Collection<AzureAppGatewayDescription> result = []

    try {
      def currentTime = System.currentTimeMillis()
      def appGateways = executeOp({
        azure.applicationGateways().list()
      })

      appGateways.each { item ->
        if (item.innerModel().location() == region) {
          try {
            def agItem = AzureAppGatewayDescription.getDescriptionForAppGateway(item.innerModel())
            agItem.dnsName = getDnsNameForPublicIp(
              AzureUtilities.getResourceGroupNameFromResourceId(item.id()),
              AzureUtilities.getNameFromResourceId(item.defaultPublicFrontend().publicIpAddressId())
            )
            agItem.lastReadTime = currentTime
            result << agItem
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the
            // load balancers
            log.error("Unable to process application gateway ${item.name()}: ${re.message}")
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
  Response deleteAppGateway(String resourceGroupName, String appGatewayName) {
    Response result
    def appGateway = executeOp({
      azure.applicationGateways().getByResourceGroup(resourceGroupName, appGatewayName)
    })

    if (appGateway?.tags()?.cluster) {
      // The selected can not be deleted because there are active server groups associated with
      def errMsg = "Failed to delete ${appGatewayName}; the application gateway is still associated with server groups in ${appGateway?.tags()?.cluster} cluster. Please delete associated server groups before deleting the load balancer."
      log.error(errMsg)
      throw new RuntimeException(errMsg)
    }

    // TODO: retrieve private IP address name when support for it is added
    // First item in the application gateway frontend IP configurations is the public IP address we are loking for
    def publicIpAddressName = AzureUtilities.getNameFromResourceId(appGateway?.defaultPublicFrontend().publicIpAddressId())

    result = deleteAzureResource(
      azure.applicationGateways().&deleteByResourceGroup,
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
   * Returns the resource ID of a backend address pool for the given application gateway.
   */
  String getAppGatewayPoolId(String resourceGroupName, String appGatewayName, String backendPoolName) {
    if (!backendPoolName) return null
    buildAppGatewayPoolId(resourceGroupName, appGatewayName, backendPoolName)
  }

  /**
   * Returns the resource ID of a backend address pool for the given load balancer.
   */
  String getLoadBalancerPoolId(String resourceGroupName, String loadBalancerName, String backendPoolName) {
    if (!backendPoolName) return null
    buildLoadBalancerPoolId(resourceGroupName, loadBalancerName, backendPoolName)
  }

  // Find unused port range. The usedList is the type List<int[]> whose element is int[2]{portStart, portEnd}
  // The usedList needs to be sorted in asc order for the element[0]
  private int findUnusedPortsRange(List<int[]> usedList, int start, int end, int targetLength) {
    int ret = start
    int retEnd = ret + targetLength
    if (retEnd > end) return -1
    for (int[] p : usedList) {
      if(p[0] > retEnd) return ret
      ret = p[1] + 1
      retEnd = ret + targetLength
      if (retEnd > end) return -1
    }
    return ret
  }

  /**
   * Enables a server group by adding a backend pool to its VMSS NIC configuration.
   */
  void enableServerGroupWithAppGateway(String resourceGroupName, String appGatewayResourceGroupName, String appGatewayName, String serverGroupName, String backendPoolName) {
    if (!backendPoolName) return
    def poolId = buildAppGatewayPoolId(appGatewayResourceGroupName, appGatewayName, backendPoolName)

    executeOp({
      def vmss = azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
      if (!vmss) throw new RuntimeException("VMSS ${serverGroupName} not found in ${resourceGroupName}")
      def ipConfig = getPrimaryIpConfig(vmss)
      def pools = ipConfig.applicationGatewayBackendAddressPools() ?: []
      if (!pools.any { it.id()?.equalsIgnoreCase(poolId) }) {
        pools = new ArrayList<>(pools)
        pools.add(new SubResource().withId(poolId))
        ipConfig.withApplicationGatewayBackendAddressPools(pools)
        vmss.update().apply()
        log.info("Added AG backend pool to VMSS ${vmss.name()}")
      }
    })

    // Tag the App Gateway with the active server group for debugging
    def appGateway = executeOp({
      azure.applicationGateways().getByResourceGroup(appGatewayResourceGroupName, appGatewayName)
    })
    if (appGateway) {
      executeOp({ appGateway.update().withTag("trafficEnabledSG", serverGroupName).apply() })
    }
  }

  /**
   * Disables a server group by removing a backend pool from its VMSS NIC configuration.
   */
  void disableServerGroup(String resourceGroupName, String appGatewayResourceGroupName, String appGatewayName, String serverGroupName, String backendPoolName) {
    if (!backendPoolName) return
    def poolId = buildAppGatewayPoolId(appGatewayResourceGroupName, appGatewayName, backendPoolName)

    executeOp({
      def vmss = azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
      if (!vmss) throw new RuntimeException("VMSS ${serverGroupName} not found in ${resourceGroupName}")
      def ipConfig = getPrimaryIpConfig(vmss)
      def pools = ipConfig.applicationGatewayBackendAddressPools()
      if (pools) {
        def updatedPools = pools.findAll { !it.id()?.equalsIgnoreCase(poolId) }
        if (updatedPools.size() < pools.size()) {
          ipConfig.withApplicationGatewayBackendAddressPools(updatedPools)
          vmss.update().apply()
          log.info("Removed AG backend pool from VMSS ${vmss.name()}")
        }
      }
    })

    def appGateway = executeOp({
      azure.applicationGateways().getByResourceGroup(appGatewayResourceGroupName, appGatewayName)
    })
    if (appGateway) {
      executeOp({ appGateway.update().withoutTag("trafficEnabledSG").apply() })
    }
  }

  /**
   * Checks if a server group, not associated with a load balancer, is "enabled".
   * Uses instance count of 0 to proxy saying it is disabled.
   */
  Boolean isServerGroupWithoutLoadBalancerDisabled(String resourceGroupName, String serverGroupName) {
    VirtualMachineScaleSet scaleSet = executeOp({
      azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
    })

    VirtualMachineScaleSetVMs machines = scaleSet.virtualMachines()
    machines.list().size() == 0
  }

  /**
   * Checks if a server group's VMSS is associated with a backend pool on the Application Gateway.
   * @return true if NOT associated (disabled), false if associated (enabled)
   */
  Boolean isServerGroupWithAppGatewayDisabled(String resourceGroupName, String appGatewayResourceGroupName, String appGatewayName, String serverGroupName, String backendPoolName) {
    if (!backendPoolName) return true
    def poolId = buildAppGatewayPoolId(appGatewayResourceGroupName, appGatewayName, backendPoolName)

    def vmss = executeOp({
      azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
    })
    if (!vmss) return true

    def ipConfig = getPrimaryIpConfigOrNull(vmss)
    if (!ipConfig) return true

    def pools = ipConfig.applicationGatewayBackendAddressPools()
    return !(pools?.any { it.id()?.equalsIgnoreCase(poolId) })
  }

  /**
   * Enables a server group by adding a backend pool to its VMSS NIC configuration.
   */
  void enableServerGroupWithLoadBalancer(String resourceGroupName, String loadBalancerName, String serverGroupName, String backendPoolName) {
    if (!backendPoolName) return
    def poolId = buildLoadBalancerPoolId(resourceGroupName, loadBalancerName, backendPoolName)

    executeOp({
      def vmss = azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
      if (!vmss) throw new RuntimeException("VMSS ${serverGroupName} not found in ${resourceGroupName}")
      def ipConfig = getPrimaryIpConfig(vmss)
      def pools = ipConfig.loadBalancerBackendAddressPools() ?: []
      if (!pools.any { it.id()?.equalsIgnoreCase(poolId) }) {
        pools = new ArrayList<>(pools)
        pools.add(new SubResource().withId(poolId))
        ipConfig.withLoadBalancerBackendAddressPools(pools)
        vmss.update().apply()
        log.info("Added LB backend pool to VMSS ${vmss.name()}")
      }
    })
  }

  /**
   * Disables a server group by removing a backend pool from its VMSS NIC configuration.
   */
  void disableServerGroupWithLoadBalancer(String resourceGroupName, String loadBalancerName, String serverGroupName, String backendPoolName) {
    if (!backendPoolName) return
    def poolId = buildLoadBalancerPoolId(resourceGroupName, loadBalancerName, backendPoolName)

    executeOp({
      def vmss = azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
      if (!vmss) throw new RuntimeException("VMSS ${serverGroupName} not found in ${resourceGroupName}")
      def ipConfig = getPrimaryIpConfig(vmss)
      def pools = ipConfig.loadBalancerBackendAddressPools()
      if (pools) {
        def updatedPools = pools.findAll { !it.id()?.equalsIgnoreCase(poolId) }
        if (updatedPools.size() < pools.size()) {
          ipConfig.withLoadBalancerBackendAddressPools(updatedPools)
          vmss.update().apply()
          log.info("Removed LB backend pool from VMSS ${vmss.name()}")
        }
      }
    })
  }

  /**
   * Checks if a server group's VMSS is associated with a backend pool on the Load Balancer.
   * @return true if NOT associated (disabled), false if associated (enabled)
   */
  Boolean isServerGroupWithLoadBalancerDisabled(String resourceGroupName, String loadBalancerName, String serverGroupName, String backendPoolName) {
    if (!backendPoolName) return true
    def poolId = buildLoadBalancerPoolId(resourceGroupName, loadBalancerName, backendPoolName)

    def vmss = executeOp({
      azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
    })
    if (!vmss) return true

    def ipConfig = getPrimaryIpConfigOrNull(vmss)
    if (!ipConfig) return true

    def pools = ipConfig.loadBalancerBackendAddressPools()
    return !(pools?.any { it.id()?.equalsIgnoreCase(poolId) })
  }

  private String buildLoadBalancerPoolId(String resourceGroupName, String loadBalancerName, String poolName) {
    "/subscriptions/${subscriptionId}/resourceGroups/${resourceGroupName}/providers/Microsoft.Network/loadBalancers/${loadBalancerName}/backendAddressPools/${poolName}"
  }

  private String buildAppGatewayPoolId(String resourceGroupName, String appGatewayName, String poolName) {
    "/subscriptions/${subscriptionId}/resourceGroups/${resourceGroupName}/providers/Microsoft.Network/applicationGateways/${appGatewayName}/backendAddressPools/${poolName}"
  }

  private VirtualMachineScaleSet requireVmss(String resourceGroupName, String serverGroupName) {
    def vmss = executeOp({
      azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
    })
    if (!vmss) {
      throw new RuntimeException("Virtual Machine Scale Set ${serverGroupName} not found in resource group ${resourceGroupName}")
    }
    vmss
  }

  private VirtualMachineScaleSetIpConfiguration getPrimaryIpConfig(VirtualMachineScaleSet vmss) {
    def ipConfig = getPrimaryIpConfigOrNull(vmss)
    if (!ipConfig) {
      throw new RuntimeException("No NIC/IP configurations found on VMSS ${vmss.name()}")
    }
    ipConfig
  }

  private VirtualMachineScaleSetIpConfiguration getPrimaryIpConfigOrNull(VirtualMachineScaleSet vmss) {
    def nicConfigs = vmss.innerModel().virtualMachineProfile().networkProfile().networkInterfaceConfigurations()
    if (!nicConfigs || nicConfigs.isEmpty()) return null
    def ipConfigs = nicConfigs.get(0).ipConfigurations()
    if (!ipConfigs || ipConfigs.isEmpty()) return null
    ipConfigs.get(0)
  }

  /**
   * Delete a network security group in Azure
   * @param resourceGroupName name of the resource group where the security group was created (see application name and region/location)
   * @param securityGroupName name of the Azure network security group to delete
   * @return a ServiceResponse object
   */
  Response deleteSecurityGroup(String resourceGroupName, String securityGroupName) {
    def associatedSubnets = azure.networkSecurityGroups().getByResourceGroup(resourceGroupName, securityGroupName).listAssociatedSubnets()

    associatedSubnets?.each{ associatedSubnet ->
      def subnetName = associatedSubnet.innerModel().name()
      associatedSubnet
        .parent()
        .update()
        .updateSubnet(subnetName)
        .withoutNetworkSecurityGroup()
        .parent()
        .apply()
    }

    deleteAzureResource(
      azure.networkSecurityGroups().&deleteByResourceGroup,
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

      //Create the virtual network for the resource group
      azure.networks()
        .define(virtualNetworkName)
        .withRegion(region)
        .withExistingResourceGroup(resourceGroupName)
        .withAddressSpace(addressPrefix)
        .create()
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
    def virtualNetwork = azure.networks().getByResourceGroup(resourceGroupName, virtualNetworkName)

    if (virtualNetwork == null) {
      def error = "Virtual network: ${virtualNetwork} not found when creating subnet: ${subnetName}"
      log.error error
      throw new RuntimeException(error)
    }

    def chain = virtualNetwork.update()
      .defineSubnet(subnetName)
      .withAddressPrefix(addressPrefix)

    if (securityGroupName) {
      def sg = azure.networkSecurityGroups().getByResourceGroup(resourceGroupName, securityGroupName)
      chain.withExistingNetworkSecurityGroup(sg)
    }

    chain.attach()
      .apply()

    virtualNetwork.subnets().get(subnetName).innerModel().id()
  }

  /**
   * Delete a subnet in the virtual network specified
   * @param resourceGroupName Resource Group in Azure where vNet and Subnet will exist
   * @param virtualNetworkName Virtual Network to create the subnet in
   * @param subnetName Name of subnet to create
   * @throws RuntimeException Throws RuntimeException if operation response indicates failure
   * @return a ServiceResponse object
   */
  Response<Void> deleteSubnet(String resourceGroupName, String virtualNetworkName, String subnetName) {

    deleteAzureResource(
      {
        String _resourceGroupName, String _subnetName, String _virtualNetworkName ->
          azure.networks().getByResourceGroup(_resourceGroupName, _virtualNetworkName).update()
            .withoutSubnet(_subnetName)
            .apply()
      },
      resourceGroupName,
      subnetName,
      virtualNetworkName,
      "Delete subnet ${subnetName}",
      "Failed to delete subnet ${subnetName} in ${resourceGroupName}"
    )
  }

  /**
   * Retrieve a collection of all network security groups for a give set of credentials and the location
   * @param region the location of the network security group
   * @return a Collection of objects which represent a Network Security Group in Azure
   */
  Collection<AzureSecurityGroupDescription> getNetworkSecurityGroupsAll(String region) {
    def result = new ArrayList<AzureSecurityGroupDescription>()

    try {
      def securityGroups = executeOp({
        azure.networkSecurityGroups().list()
      })
      def currentTime = System.currentTimeMillis()
      securityGroups?.each { item ->
        if (item.innerModel().location() == region) {
          try {
            def sgItem = getAzureSecurityGroupDescription(item.innerModel())
            sgItem.lastReadTime = currentTime
            result += sgItem
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the
            // NSGs
            log.error("Unable to process network security group ${item.name()}: ${re.message}")
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
      def securityGroup = executeOp({
        azure.networkSecurityGroups().getByResourceGroup(resourceGroupName, securityGroupName)
      })
      def currentTime = System.currentTimeMillis()
      def sgItem = getAzureSecurityGroupDescription(securityGroup.innerModel())
      sgItem.lastReadTime = currentTime

      return sgItem
    } catch (Exception e) {
      log.error("getNetworkSecurityGroupsAll -> Unexpected exception ", e)
    }

    null
  }

  private static AzureSecurityGroupDescription getAzureSecurityGroupDescription(NetworkSecurityGroupInner item) {
    def sgItem = new AzureSecurityGroupDescription()
    sgItem.name = item.name()
    sgItem.id = item.name()
    sgItem.location = item.location()
    sgItem.region = item.name()
    sgItem.cloudProvider = "azure"
    sgItem.provisioningState = item.provisioningState()
    sgItem.resourceGuid = item.id()
    sgItem.resourceId = item.id()
    sgItem.tags.putAll(item.tags())
    def parsedName = Names.parseName(item.name())
    sgItem.stack = item.tags()?.stack ?: parsedName.stack
    sgItem.detail = item.tags()?.detail ?: parsedName.detail
    sgItem.appName = item.tags()?.appName ?: parsedName.app
    sgItem.createdTime = item.tags()?.createdTime?.toLong()
    sgItem.type = item.type()
    sgItem.securityRules = new ArrayList<AzureSecurityGroupDescription.AzureSGRule>()
    item.securityRules().each { rule ->
      sgItem.securityRules += new AzureSecurityGroupDescription.AzureSGRule(
        resourceId: rule.id(),
        id: rule.name(),
        name: rule.name(),
        access: rule.access().toString(),
        priority: rule.priority(),
        protocol: rule.protocol().toString(),
        direction: rule.direction().toString(),
        destinationAddressPrefix: rule.destinationAddressPrefix(),
        destinationPortRange: rule.destinationPortRange(),
        destinationPortRanges: rule.destinationPortRanges(),
        destinationPortRangeModel: rule.destinationPortRange() ? rule.destinationPortRange() : rule.destinationPortRanges()?.toString()?.replaceAll("[^(0-9),-]", ""),
        sourceAddressPrefix: rule.sourceAddressPrefix(),
        sourceAddressPrefixes: rule.sourceAddressPrefixes(),
        sourceAddressPrefixModel: rule.sourceAddressPrefix() ? rule.sourceAddressPrefix() : rule.sourceAddressPrefixes()?.toString()?.replaceAll("[^(0-9a-zA-Z)./,:]", ""),
        sourcePortRange: rule.sourcePortRange())
    }

    sgItem.subnets = new ArrayList<String>()
    item.subnets()?.each { sgItem.subnets += AzureUtilities.getNameFromResourceId(it.id()) }
    sgItem.networkInterfaces = new ArrayList<String>()
    item.networkInterfaces()?.each { sgItem.networkInterfaces += it.id() }

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
      def vnets = executeOp({
        azure.networks().list()
      })
      def currentTime = System.currentTimeMillis()
      vnets?.each { item ->
        if (item.innerModel().location() == region) {
          try {
            AzureSubnetDescription.getSubnetsForVirtualNetwork(item.innerModel()).each { AzureSubnetDescription subnet ->
              subnet.lastReadTime = currentTime
              result += subnet
            }
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the subnets
            log.error("Unable to process subnets for virtual network ${item.name()}", re)
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
  Network getVirtualNetwork(String resourceGroupName, String virtualNetworkName) {
    executeOp({
      azure.networks().getByResourceGroup(resourceGroupName, virtualNetworkName)
    })
  }

  /**
   * Retrieve a collection of all virtual networks for a give set of credentials and the location
   * @param region the location of the virtual network
   * @return a Collection of objects which represent a Virtual Network in Azure
   */
  Collection<AzureVirtualNetworkDescription> getVirtualNetworksAll(String region) {
    def result = new ArrayList<AzureVirtualNetworkDescription>()

    try {
      def vnetList = executeOp({
        azure.networks().list()
      })
      def currentTime = System.currentTimeMillis()
      vnetList?.each { item ->
        if (item.innerModel().location() == region) {
          try {
            if (item?.innerModel().addressSpace()?.addressPrefixes()?.size() != 1) {
              log.warn("Virtual Network found with ${item?.innerModel().addressSpace()?.addressPrefixes()?.size()} address spaces; expected: 1")
            }

            def vnet = AzureVirtualNetworkDescription.getDescriptionForVirtualNetwork(item.innerModel())
            vnet.subnets = AzureSubnetDescription.getSubnetsForVirtualNetwork(item.innerModel()).toList()

//            def appGateways = executeOp({ appGatewayOps.listAll() })?.body
//            AzureSubnetDescription.getAppGatewaysConnectedResources(vnet, appGateways.findAll { it.location == region })

            vnet.lastReadTime = currentTime
            result += vnet
          } catch (RuntimeException re) {
            // if we get a runtime exception here, log it but keep processing the rest of the
            // virtual networks
            log.error("Unable to process virtual network ${item.innerModel().name()}", re)
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
      PublicIpAddress publicIp = publicIpName ?
        executeOp({
          azure.publicIpAddresses().getByResourceGroup(resourceGroupName, publicIpName)
        }) : null
      if (publicIp?.fqdn()) dnsName = publicIp.fqdn()
    } catch (Exception e) {
      log.error("getDnsNameForPublicIp -> Unexpected exception ", e)
    }

    dnsName
  }

  Boolean checkDnsNameAvailability(String dnsName) {
    def isAvailable = azure.trafficManagerProfiles().checkDnsNameAvailability(dnsName)
    isAvailable
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
