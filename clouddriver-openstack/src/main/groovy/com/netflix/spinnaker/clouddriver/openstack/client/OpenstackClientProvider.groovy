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

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.OpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.apache.commons.lang.StringUtils
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.api.networking.ext.LoadBalancerService
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.RebootType
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Member

import java.lang.reflect.UndeclaredThrowableException
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Provides access to the Openstack API.
 *
 * TODO use OpenstackProviderException here instead of OpenstackOperationException.
 * Use of OpenstackOperationException belongs in Operation classes.
 *
 * TODO handleRequest should be refactored to remove the operation parameter, as client calls
 * made here do not necessarily pertain to operations.
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
   * Delete an instance.
   * @param instanceId
   * @return
   */
  void deleteInstance(String instanceId) {
    handleRequest(AtomicOperations.TERMINATE_INSTANCES) {
      client.compute().servers().delete(instanceId)
    }
  }

  /**
   * Reboot an instance ... Default to SOFT reboot if not passed.
   * @param instanceId
   * @return
   */
  void rebootInstance(String instanceId, RebootType rebootType = RebootType.SOFT) {
    handleRequest(AtomicOperations.REBOOT_INSTANCES) {
      client.compute().servers().reboot(instanceId, rebootType)
    }
  }

  /**
   * Create or update a security group, applying a list of rules. If the securityGroupId is provided, updates an existing
   * security group, else creates a new security group.
   *
   * Note: 2 default egress rules are created when creating a new security group
   * automatically with remote IP prefixes 0.0.0.0/0 and ::/0.
   *
   * @param securityGroupId id of an existing security group to update
   * @param securityGroupName name security group
   * @param description description of the security group
   * @param rules list of rules for the security group
   */
  void upsertSecurityGroup(String securityGroupId, String securityGroupName, String description, List<OpenstackSecurityGroupDescription.Rule> rules) {

    handleRequest(AtomicOperations.UPSERT_SECURITY_GROUP) {

      // The call to getClient reauthentictes via a token, so grab once for this method to avoid unnecessary reauthentications
      def securityGroupsApi = client.compute().securityGroups()

      // Try getting existing security group, update if needed
      SecGroupExtension securityGroup
      if (StringUtils.isNotEmpty(securityGroupId)) {
        securityGroup = securityGroupsApi.get(securityGroupId)
      }
      if (securityGroup == null) {
        securityGroup = securityGroupsApi.create(securityGroupName, description)
      } else {
        securityGroup = securityGroupsApi.update(securityGroup.id, securityGroupName, description)
      }

      // TODO: Find the different between existing rules and only apply that instead of deleting and re-creating all the rules
      securityGroup.rules.each { rule ->
        securityGroupsApi.deleteRule(rule.id)
      }

      rules.each { rule ->
        securityGroupsApi.createRule(Builders.secGroupRule()
          .parentGroupId(securityGroup.id)
          .protocol(IPProtocol.valueOf(rule.ruleType))
          .cidr(rule.cidr)
          .range(rule.fromPort, rule.toPort).build())
      }
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
  void deploy(String stackName, String heatTemplate, Map<String, String> parameters, boolean disableRollback, Long timeoutMins) {
    try {
      client.heat().stacks().create(stackName, heatTemplate, parameters, disableRollback, timeoutMins)
    } catch (Exception e) {
      throw new OpenstackOperationException(AtomicOperations.CREATE_SERVER_GROUP, e)
    }
    //TODO: Handle heat autoscaling migration to senlin in versions > Mitaka
  }

  /**
   * List existing heat stacks (server groups)
   * @return List < ? extends Stack >  stacks
   */
  List<? extends Stack> listStacks() {
    def stacks
    try {
      stacks = client.heat().stacks().list()
    } catch (Exception e) {
      throw new OpenstackOperationException(e)
    }
    stacks
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
   * Get the load balanacer pool associated with the id.
   * @param lbPoolId
   * @return
   */
  LbPool getLoadBalancerPool(String region, String lbPoolId) {
    LbPool pool = null
    try {
      pool = client.useRegion(region).networking().loadbalancers().lbPool().get(lbPoolId)
    } catch (Exception e) {
      throw new OpenstackProviderException("Unable to find load balancer ${lbPoolId}", e)
    }
    if (pool == null) {
      throw new OpenstackProviderException("Unable to find load balancer ${lbPoolId}")
    }
    pool
  }

  /**
   *
   * @param ip
   * @param lbPoolId
   * @param internalPort
   * @param weight
   */
  Member addMemberToLoadBalancerPool(String region, String ip, String lbPoolId, int internalPort, int weight) {
    //TODO use handleRequest once that is refactored
    try {
      client.useRegion(region).networking().loadbalancers().member().create(
        Builders.member().address(ip).poolId(lbPoolId).protocolPort(internalPort).weight(weight).build()
      )
    } catch (Exception e) {
      throw new OpenstackProviderException("Unable to add ip $ip to load balancer ${lbPoolId}", e)
    }
  }

  /**
   *
   * @param memberId
   * @return
   */
  ActionResponse removeMemberFromLoadBalancerPool(String region, String memberId) {
    //TODO use handleRequest once that is refactored
    try {
      client.useRegion(region).networking().loadbalancers().member().delete(memberId)
    } catch (Exception e) {
      throw new OpenstackProviderException("Unable to remove load balancer member $memberId", e)
    }
  }

  /**
   *
   * @param instanceId
   * @param ip
   * @param lbPool
   */
  String getMemberIdForInstance(String region, String ip, LbPool lbPool) {
    //TODO use handleRequest once that is refactored
    String memberId = ""
    try {
      memberId = client.useRegion(region).networking().loadbalancers().member().list()?.find { m -> m.address == ip }?.id
    } catch (Exception e) {
      throw new OpenstackProviderException("Failed to list load balancer members", e)
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
    //TODO use handleRequest once that is refactored
    Server server = client.useRegion(region).compute().servers().get(instanceId)
    if (server == null) {
      throw new OpenstackOperationException("Could not find server with id ${instanceId}")
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
   * Disassociates and removes health monitor from load balancer.
   * @param region
   * @param lbPoolId
   * @param healthMonitorId
   */
  void disassociateAndRemoveHealthMonitor(String region, String lbPoolId, String healthMonitorId) {
    handleRequest {
      LoadBalancerService loadBalancerService = getRegionClient(region).networking().loadbalancers()
      loadBalancerService.lbPool().disAssociateHealthMonitor(lbPoolId, healthMonitorId)
      loadBalancerService.healthMonitor().delete(healthMonitorId)
    }
  }

  /**
   * Handler for an Openstack4J request with error common handling.
   * @param operation to add context to error messages
   * @param closure makes the needed Openstack4J request
   * @return returns the result from the closure
   */
  def handleRequest(String operation, Closure closure) {
    def result
    try {
      result = closure()
    } catch (Exception e) {
      throw new OpenstackOperationException(operation, e)
    }
    if (result instanceof ActionResponse && !result.isSuccess()) {
      throw new OpenstackOperationException(result, operation)
    }
    result
  }

  /**
   * Handler for an Openstack4J request with error common handling.
   * @param closure makes the needed Openstack4J request
   * @return returns the result from the closure
   */
  def handleRequest(Closure closure) {
    def result
    try {
      result = closure()
    } catch (UndeclaredThrowableException ute) {
      throw new OpenstackProviderException('Unable to process request', ute.cause)
    } catch (Exception e) {
      throw new OpenstackProviderException('Unable to process request', e)
    }
    if (result instanceof ActionResponse && !result.isSuccess()) {
      throw new OpenstackProviderException(result)
    }
    result
  }

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

  OSClient getRegionClient(String region) {
    client.useRegion(region)
  }
}
