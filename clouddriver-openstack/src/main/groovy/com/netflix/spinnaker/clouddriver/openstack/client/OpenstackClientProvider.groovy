/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerPool
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.domain.VirtualIP
import org.apache.commons.lang.StringUtils
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.api.networking.ext.LoadBalancerService
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.RebootType
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Resource
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.heat.StackCreate
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.HealthMonitorType
import org.openstack4j.model.network.ext.LbMethod
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Member
import org.openstack4j.model.network.ext.Protocol
import org.openstack4j.model.network.ext.Vip

import java.lang.reflect.UndeclaredThrowableException
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Provides access to the Openstack API.
 *
 * TODO use OpenstackProviderException here instead of OpenstackOperationException.
 * Use of OpenstackOperationException belongs in Operation classes.
 *
 * TODO region support will need to be added to all client calls not already using regions
 *
 * TODO tokens will need to be regenerated if they are expired.
 */
abstract class OpenstackClientProvider {

  final int minPort = 1
  final int maxPort = (1 << 16) - 1
  final String lbDescriptionRegex = ".*internal_port=([0-9]+).*"
  final Pattern lbDescriptionPattern = Pattern.compile(lbDescriptionRegex)

  /**
   * Returns a list of instances in a given region.
   * @param region
   * @return
     */
  List<? extends Server> getInstances(String region) {
    handleRequest {
      getRegionClient(region).compute().servers().list()
    }
  }

  /**
   * Returns all of the console output for a given server and region.
   * @param region
   * @param serverId
   * @return
     */
  String getConsoleOutput(String region, String serverId) {
    handleRequest {
      getRegionClient(region).compute().servers().getConsoleOutput(serverId, -1)
    }
  }

  /**
   * Delete an instance.
   * @param instanceId
   * @return
   */
  void deleteInstance(String instanceId) {
    handleRequest {
      client.compute().servers().delete(instanceId)
    }
  }

  /**
   * Reboot an instance ... Default to SOFT reboot if not passed.
   * @param instanceId
   * @return
   */
  void rebootInstance(String instanceId, RebootType rebootType = RebootType.SOFT) {
    handleRequest {
      client.compute().servers().reboot(instanceId, rebootType)
    }
  }

  /**
   * Get all Load Balancer Pools for region
   * @param region
   * @return List < ? extends LbPool >
   */
  List<? extends LbPool> getAllLoadBalancerPools(final String region) {
    List<? extends LbPool> pools = handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().list()
    }
    pools
  }

  /**
   * Gets load balancer pool for a given region by load balancer UUID.
   * @param region
   * @param loadBalancerId
   * @return
   */
  LbPool getLoadBalancerPool(final String region, final String loadBalancerId) {
    LbPool result = handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().get(loadBalancerId)
    }
    if (!result) {
      throw new OpenstackProviderException("Unable to find load balancer ${loadBalancerId} in ${region}")
    }
    result
  }

  /**
   * Gets VIP for a given region.
   * @param region
   * @param vipId
   * @return
   */
  Vip getVip(final String region, final String vipId) {
    Vip result = handleRequest {
      getRegionClient(region).networking().loadbalancers().vip().get(vipId)
    }
    if (!result) {
      throw new OpenstackProviderException("Unable to find vip ${vipId} in ${region}")
    }
    result
  }

  /**
   * Validates the subnet in a region.
   * @param region
   * @param subnetId
   * @return boolean
   */
  boolean validateSubnetId(final String region, final String subnetId) {
    handleRequest {
      getRegionClient(region).networking().subnet().get(subnetId) != null
    }
  }

  /**
   * Creates load balancer pool in provided region.
   * @param region
   * @param loadBalancerPool
   * @return LbPool
   */
  LbPool createLoadBalancerPool(final String region, final LoadBalancerPool loadBalancerPool) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().create(
        Builders.lbPool()
          .name(loadBalancerPool.derivedName)
          .protocol(Protocol.forValue(loadBalancerPool.protocol?.name()))
          .lbMethod(LbMethod.forValue(loadBalancerPool.method?.name()))
          .subnetId(loadBalancerPool.subnetId)
          .description(loadBalancerPool.description)
          .adminStateUp(Boolean.TRUE)
          .build())
    }
  }

  /**
   * Updates existing load balancer pool's name or load balancer method.
   * @param region
   * @param loadBalancerPool
   * @return
   */
  LbPool updateLoadBalancerPool(final String region, final LoadBalancerPool loadBalancerPool) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().update(loadBalancerPool.id,
        Builders.lbPoolUpdate()
          .name(loadBalancerPool.derivedName)
          .lbMethod(LbMethod.forValue(loadBalancerPool.method?.name()))
          .description(loadBalancerPool.description)
          .adminStateUp(Boolean.TRUE)
          .build())
    }
  }

  /**
   * Creates VIP for given region and pool.
   * @param region
   * @param virtualIP
   * @return
   */
  Vip createVip(final String region, final VirtualIP virtualIP) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().vip().create(
        Builders.vip()
          .name(virtualIP.derivedName)
          .subnetId(virtualIP.subnetId)
          .poolId(virtualIP.poolId)
          .protocol(Protocol.forValue(virtualIP.protocol?.name()))
          .protocolPort(virtualIP.port)
          .adminStateUp(Boolean.TRUE)
          .build())
    }
  }

  /**
   * Updates VIP in specified region.
   * @param region
   * @param virtualIP
   * @return
   */
  Vip updateVip(final String region, final VirtualIP virtualIP) {
    handleRequest {
      // TODO - Currently only supporting updates to name ... Expanded to update SessionPersistence & connectionLimit
      getRegionClient(region).networking().loadbalancers().vip().update(virtualIP.id,
        Builders.vipUpdate().name(virtualIP.derivedName).adminStateUp(Boolean.TRUE).build())
    }
  }

  /**
   * Gets HealthMonitor for given region and id.
   * @param region
   * @param healthMonitorId
   * @return
   */
  HealthMonitor getHealthMonitor(final String region, final String healthMonitorId) {
    HealthMonitor result = handleRequest {
      getRegionClient(region).networking().loadbalancers().healthMonitor().get(healthMonitorId)
    }
    if (!result) {
      throw new OpenstackProviderException("Unable to find health monitor with ${healthMonitorId} in ${region}")
    }
    result
  }

  /**
   * Creates health check for given pool in specified region.
   * @param region
   * @param lbPoolId
   * @param monitor
   * @return
   */
  HealthMonitor createHealthCheckForPool(final String region, final String lbPoolId, final PoolHealthMonitor monitor) {
    LoadBalancerService loadBalancerService = getRegionClient(region).networking().loadbalancers()
    HealthMonitor result = handleRequest {
      loadBalancerService.healthMonitor().create(
        Builders.healthMonitor()
          .type(HealthMonitorType.forValue(monitor.type?.name()))
          .delay(monitor.delay)
          .timeout(monitor.timeout)
          .maxRetries(monitor.maxRetries)
          .httpMethod(monitor.httpMethod)
          .urlPath(monitor.url)
          .expectedCodes(monitor.expectedHttpStatusCodes?.join(','))
          .adminStateUp(Boolean.TRUE)
          .build())
    }

    // Check that the health monitor was created successfully or throw exception
    if (!result) {
      throw new OpenstackProviderException("Unable to create health check for pool ${lbPoolId}")
    } else {
      result = handleRequest {
        loadBalancerService.lbPool().associateHealthMonitor(lbPoolId, result.id)
      }
    }
    result
  }

  /**
   * Updates health monitor for a given region.
   * @param region
   * @param monitor
   * @return
   */
  HealthMonitor updateHealthMonitor(final String region, final PoolHealthMonitor monitor) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().healthMonitor().update(monitor.id,
        Builders.healthMonitorUpdate()
          .delay(monitor.delay)
          .timeout(monitor.timeout)
          .maxRetries(monitor.maxRetries)
          .httpMethod(monitor.httpMethod)
          .urlPath(monitor.url)
          .expectedCodes(monitor.expectedHttpStatusCodes?.join(','))
          .adminStateUp(Boolean.TRUE)
          .build())
    }
  }

  /**
   * Disassociates and removes health monitor from load balancer.
   * @param region
   * @param lbPoolId
   * @param healthMonitorId
   */
  void disassociateAndRemoveHealthMonitor(String region, String lbPoolId, String healthMonitorId) {
    disassociateHealthMonitor(region, lbPoolId, healthMonitorId)
    deleteHealthMonitor(region, healthMonitorId)
  }

  /**
   * Deletes a health monitor.
   * @param region
   * @param healthMonitorId
   * @return
   */
  ActionResponse deleteHealthMonitor(String region, String healthMonitorId) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().healthMonitor().delete(healthMonitorId)
    }
  }

  /**
   * Disassociates health monitor from loadbalancer pool.
   * @param region
   * @param lbPoolId
   * @param healMonitorId
   */
  ActionResponse disassociateHealthMonitor(String region, String lbPoolId, String healthMonitorId) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().disAssociateHealthMonitor(lbPoolId, healthMonitorId)
    }
  }

  /**
   * Associate already known floating IP address to VIP in specified region.
   * @param region
   * @param floatingIpId
   * @param vipId
   * @return
   */
  NetFloatingIP associateFloatingIpToVip(final String region, final String floatingIpId, final String vipId) {
    Port port = getPortForVip(region, vipId)
    if (!port) {
      throw new OpenstackProviderException("Unable to find port for vip ${vipId}")
    } else {
      handleRequest {
        getRegionClient(region).networking().floatingip().associateToPort(floatingIpId, port.id)
      }
    }
  }

  /**
   * Remove port associated with floating IP.
   * @param region
   * @param floatingIpId
   * @return
   */
  NetFloatingIP disassociateFloatingIp(final String region, final String floatingIpId) {
    handleRequest {
      getRegionClient(region).networking().floatingip().disassociateFromPort(floatingIpId)
    }
  }

  /**
   * Looks up the port associated by vip and uses the deviceId to get the attached floatingIp.
   * @param region
   * @param vipId
   * @return
   */
  FloatingIP getAssociatedFloatingIp(final String region, final String vipId) {
    Port port = getPortForVip(region, vipId)
    if (!port) {
      throw new OpenstackProviderException("Unable to find port for vip ${vipId}")
    } else {
      handleRequest {
        getRegionClient(region).compute().floatingIps().list()?.find { it.instanceId == port.deviceId }
      }
    }
  }

  /**
   * Internal helper to look up port associated to vip.
   * @param region
   * @param vipId
   * @return
   */
  protected Port getPortForVip(final String region, final String vipId) {
    handleRequest {
      getRegionClient(region).networking().port().list()?.find { it.name == "vip-${vipId}" }
    }
  }

  /**
   * Deletes a security group rule
   * @param region the region to delete the rule from
   * @param id id of the rule to delete
   */
  void deleteSecurityGroupRule(String region, String id) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().deleteRule(id)
    }
  }

  /**
   * Creates a security group rule.
   * @param region the region to create the rule in
   * @param securityGroupId id of the security group which this rule belongs to
   * @param protocol the protocol of the rule
   * @param cidr the cidr for the rule
   * @param fromPort the fromPort for the rule
   * @param toPort the toPort for the rule
   * @return the created rule
   */
  SecGroupExtension.Rule createSecurityGroupRule(String region, String securityGroupId, IPProtocol protocol, String cidr, int fromPort, int toPort) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().createRule(Builders.secGroupRule()
        .parentGroupId(securityGroupId)
        .protocol(protocol)
        .cidr(cidr)
        .range(fromPort, toPort)
        .build())
    }
  }

  /**
   * Updates a security group with the new name and description
   * @param region the region the security group is in
   * @param id the id of the security group to update
   * @param name the new name for the security group
   * @param description the new description for the security group
   * @return the updated security group
   */
  SecGroupExtension updateSecurityGroup(String region, String id, String name, String description) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().update(id, name, description)
    }
  }

  /**
   * Creates a security group with the given name and description
   * @return the created security group
   */
  SecGroupExtension createSecurityGroup(String region, String name, String description) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().create(name, description)
    }
  }

  /**
   * Returns the security group for the given id.
   * @param region the region to look up the security group in
   * @param id id of the security group.
   */
  SecGroupExtension getSecurityGroup(String region, String id) {
    SecGroupExtension securityGroup = handleRequest {
      client.useRegion(region).compute().securityGroups().get(id)
    }
    if (!securityGroup) {
      throw new OpenstackResourceNotFoundException("Unable to find security group ${id}")
    }
    securityGroup
  }

  /**
   * Returns the list of all security groups for the given region
   */
  List<SecGroupExtension> getSecurityGroups(String region) {
    handleRequest {
      getRegionClient(region).compute().securityGroups().list()
    }
  }

  /**
   * Create a Spinnaker Server Group (Openstack Heat Stack).
   * @param stackName
   * @param heatTemplate
   * @param parameters
   * @param disableRollback
   * @param timeoutMins
   * @return
   */
  void deploy(String region, String stackName, String template, Map<String, String> subtemplate, ServerGroupParameters parameters, boolean disableRollback, Long timeoutMins) {

    handleRequest {

      Map<String, String> params = parameters.toParamsMap()

      StackCreate create = Builders.stack()
        .name(stackName)
        .template(template)
        .parameters(params)
        .files(subtemplate)
        .disableRollback(disableRollback)
        .timeoutMins(timeoutMins)
        .build()

      getRegionClient(region).heat().stacks().create(create)

    }

    //TODO: Handle heat autoscaling migration to senlin in versions > Mitaka

  }

  /**
   * Get a heat template from an existing Openstack Heat Stack
   * @param region
   * @param stackName
   * @param stackId
   * @return
   */
  String getHeatTemplate(String region, String stackName, String stackId) {
    try {
      def template = client.useRegion(region).heat().templates().getTemplateAsString(stackName, stackId)
      return template
    } catch (Exception e) {
      throw new OpenstackOperationException(e)
    }
  }

  /**
   * List existing heat stacks (server groups)
   * @return List < ? extends Stack >  stacks
   */
  List<? extends Stack> listStacks(String region) {
    handleRequest {
      getRegionClient(region).heat().stacks().list()
    }
  }

  /**
   * Get a stack in a specific region.
   * @param stackName
   * @return
   */
  Stack getStack(String region, String stackName) {
    Stack stack = handleRequest {
      client.useRegion(region).heat().stacks().getStackByName(stackName)
    }
    if (!stack) {
      throw new OpenstackProviderException("Unable to find stack $stackName in region $region")
    }
    stack
  }

  /**
   * Delete a stack in a specific region.
   * @param stack
   */
  void destroy(String region, Stack stack) {
    handleRequest {
      client.useRegion(region).heat().stacks().delete(stack.name, stack.id)
    }
  }

  /**
   * Get all instance ids of server resources associated with a stack.
   * @param region
   * @param stackName
   */
  List<String> getInstanceIdsForStack(String region, String stackName) {
    List<? extends Resource> resources = handleRequest {
      getRegionClient(region).heat().resources().list(stackName)
    }
    List<String> ids = resources.findResults {
      it.type == "OS::Nova::Server" ? it.physicalResourceId : null
    }
    ids
  }

  /**
   * Get port from load balancer description. Openstack load balancers have no native concept of internal port,
   * so we store in the description field of the load balancer.
   * this may be changed in a future version.
   * @param pool
   * @return
   */
  int getInternalLoadBalancerPort(LbPool pool) {
    Matcher matcher = lbDescriptionPattern.matcher(pool.description)
    int internalPort = 0
    if (matcher.matches()) {
      internalPort = matcher.group(1).toInteger()
    }
    if (internalPort < minPort || internalPort > maxPort) {
      throw new OpenstackProviderException("Internal pool port $internalPort is outside of the valid range.")
    }
    internalPort
  }

  /**
   * Get an IP address from a server.
   * @param server
   * @return
   */
  String getIpForInstance(String region, String instanceId) {
    Server server = getServerInstance(region, instanceId)
    /* TODO
      For now just get the first address found. Openstack does not associate an instance id
      with load balancer membership, just an ip address. An instance can have multiple IP addresses.
      perhaps we just look for the first 192.* address found. It would also help to know the network name
      from which to choose the IP list. I am not sure if we will have that. We can certainly add that into
      the api later on when we know what data deck will have access to.
    */
    String ip = server.addresses?.addresses?.collect { n -> n.value }?.find()?.find()?.addr
    if (StringUtils.isEmpty(ip)) {
      throw new OpenstackProviderException("Instance ${instanceId} has no IP address")
    }
    ip
  }

  /**
   *
   * @param ip
   * @param lbPoolId
   * @param internalPort
   * @param weight
   */
  Member addMemberToLoadBalancerPool(String region, String ip, String lbPoolId, int internalPort, int weight) {
    Member member = handleRequest {
      client.useRegion(region).networking().loadbalancers().member().create(
        Builders.member().address(ip).poolId(lbPoolId).protocolPort(internalPort).weight(weight).build()
      )
    }
    if (!member) {
      throw new OpenstackProviderException("Unable to add ip $ip to load balancer ${lbPoolId}")
    }
    member
  }

  /**
   *
   * @param memberId
   * @return
   */
  ActionResponse removeMemberFromLoadBalancerPool(String region, String memberId) {
    handleRequest {
      client.useRegion(region).networking().loadbalancers().member().delete(memberId)
    }
  }

  /**
   *
   * @param instanceId
   * @param ip
   * @param lbPool
   */
  String getMemberIdForInstance(String region, String ip, LbPool lbPool) {
    String memberId = handleRequest {
      client.useRegion(region).networking().loadbalancers().member().list()?.find { m -> m.address == ip }?.id
    }
    if (StringUtils.isEmpty(memberId)) {
      throw new OpenstackProviderException("Instance with ip ${ip} is not associated with any load balancer memberships")
    }
    if (lbPool.members.find { it == memberId } == null) {
      throw new OpenstackProviderException("Member id ${memberId} is not associated with load balancer with id ${lbPool.id}")
    }
    memberId
  }
  /**
   * Get a compute server based on id.
   * @param instanceId
   * @return
   */
  Server getServerInstance(String region, String instanceId) {
    Server server = handleRequest {
      client.useRegion(region).compute().servers().get(instanceId)
    }
    if (!server) {
      throw new OpenstackProviderException("Could not find server with id ${instanceId}")
    }
    server
  }

  /**
   * Remove load balancer pool.
   * @param region
   * @param poolId
   */
  void deleteVip(String region, String vipId) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().vip().delete(vipId)
    }
  }

  /**
   * Remove load balancer pool.
   * @param region
   * @param poolId
   */
  void deleteLoadBalancerPool(String region, String poolId) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().delete(poolId)
    }
  }

  /**
   * Deletes a security group.
   *
   * @param region the region the security group is in
   * @param securityGroupId id of the security group
   */
  void deleteSecurityGroup(String region, String securityGroupId) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().delete(securityGroupId)
    }
  }

  /**
   * Handler for an Openstack4J request with error common handling.
   * @param closure makes the needed Openstack4J request
   * @return returns the result from the closure
   */
  static <T> T handleRequest(Closure<T> closure) {
    T result
    try {
      result = closure()
    } catch (UndeclaredThrowableException e) {
      throw new OpenstackProviderException('Unable to process request', e.cause)
    } catch (Exception e) {
      throw new OpenstackProviderException('Unable to process request', e)
    }
    if (result instanceof ActionResponse && !result.isSuccess()) {
      throw new OpenstackProviderException(result)
    }
    result
  }

  /**
   * Returns a list of regions.
   * @return
   */
  abstract List<String> getAllRegions()

  /**
   * Thread-safe way to get client.
   * @return
   */
  abstract OSClient getClient()

  /**
   * Get a new token id.
   * @return
   */
  abstract String getTokenId()

  /**
   * Helper method to get region based thread-safe OS client.
   * @param region
   * @return
   */
  OSClient getRegionClient(String region) {
    client.useRegion(region)
  }
}
