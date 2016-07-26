/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerPool
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.VirtualIP
import org.apache.commons.lang.StringUtils
import org.openstack4j.api.Builders
import org.openstack4j.api.networking.ext.LoadBalancerService
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.HealthMonitorType
import org.openstack4j.model.network.ext.LbMethod
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Member
import org.openstack4j.model.network.ext.Protocol
import org.openstack4j.model.network.ext.Vip

import java.util.regex.Matcher
import java.util.regex.Pattern

class OpenstackNetworkingV2Provider implements OpenstackNetworkingProvider, OpenstackRequestHandler, OpenstackIdentityAware {

  final int minPort = 1
  final int maxPort = (1 << 16) - 1
  final String lbDescriptionRegex = ".*internal_port=([0-9]+).*"
  final Pattern lbDescriptionPattern = Pattern.compile(lbDescriptionRegex)

  OpenstackIdentityProvider identityProvider

  OpenstackNetworkingV2Provider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider
  }

  @Override
  List<? extends LbPool> getAllLoadBalancerPools(final String region) {
    List<? extends LbPool> pools = handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().list()
    }
    pools
  }

  @Override
  LbPool getLoadBalancerPool(final String region, final String loadBalancerId) {
    LbPool result = handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().get(loadBalancerId)
    }
    if (!result) {
      throw new OpenstackProviderException("Unable to find load balancer ${loadBalancerId} in ${region}")
    }
    result
  }

  @Override
  List<? extends Vip> listVips(final String region) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().vip().list()
    }
  }

  @Override
  Vip getVip(final String region, final String vipId) {
    Vip result = handleRequest {
      getRegionClient(region).networking().loadbalancers().vip().get(vipId)
    }
    if (!result) {
      throw new OpenstackProviderException("Unable to find vip ${vipId} in ${region}")
    }
    result
  }

  @Override
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

  @Override
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

  @Override
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

  @Override
  Vip updateVip(final String region, final VirtualIP virtualIP) {
    handleRequest {
      // TODO - Currently only supporting updates to name ... Expanded to update SessionPersistence & connectionLimit
      getRegionClient(region).networking().loadbalancers().vip().update(virtualIP.id,
        Builders.vipUpdate().name(virtualIP.derivedName).adminStateUp(Boolean.TRUE).build())
    }
  }

  @Override
  HealthMonitor getHealthMonitor(final String region, final String healthMonitorId) {
    HealthMonitor result = handleRequest {
      getRegionClient(region).networking().loadbalancers().healthMonitor().get(healthMonitorId)
    }
    if (!result) {
      throw new OpenstackProviderException("Unable to find health monitor with ${healthMonitorId} in ${region}")
    }
    result
  }

  @Override
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

  @Override
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

  @Override
  void disassociateAndRemoveHealthMonitor(String region, String lbPoolId, String healthMonitorId) {
    disassociateHealthMonitor(region, lbPoolId, healthMonitorId)
    deleteHealthMonitor(region, healthMonitorId)
  }

  @Override
  ActionResponse deleteHealthMonitor(String region, String healthMonitorId) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().healthMonitor().delete(healthMonitorId)
    }
  }

  @Override
  ActionResponse disassociateHealthMonitor(String region, String lbPoolId, String healthMonitorId) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().disAssociateHealthMonitor(lbPoolId, healthMonitorId)
    }
  }

  @Override
  Network getNetwork(final String region, final String networkId) {
    handleRequest {
      getRegionClient(region).networking().network().list().find { it.id == networkId }
    }
  }

  @Override
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

  @Override
  NetFloatingIP disassociateFloatingIp(final String region, final String floatingIpId) {
    handleRequest {
      getRegionClient(region).networking().floatingip().disassociateFromPort(floatingIpId)
    }
  }

  @Override
  List<? extends Port> listPorts(final String region) {
    handleRequest {
      getRegionClient(region).networking().port().list()
    }
  }

  @Override
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

  @Override
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

  @Override
  ActionResponse removeMemberFromLoadBalancerPool(String region, String memberId) {
    handleRequest {
      client.useRegion(region).networking().loadbalancers().member().delete(memberId)
    }
  }

  @Override
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

  @Override
  void deleteVip(String region, String vipId) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().vip().delete(vipId)
    }
  }

  @Override
  void deleteLoadBalancerPool(String region, String poolId) {
    handleRequest {
      getRegionClient(region).networking().loadbalancers().lbPool().delete(poolId)
    }
  }

  @Override
  List<Network> listNetworks(String region) {
    handleRequest {
      getRegionClient(region).networking().network().list()
    }
  }

  @Override
  List<Subnet> listSubnets(String region) {
    handleRequest {
      getRegionClient(region).networking().subnet().list()
    }
  }

  @Override
  Subnet getSubnet(final String region, final String subnetId) {
    handleRequest {
      getRegionClient(region).networking().subnet().get(subnetId)
    }
  }

  @Override
  Port getPortForVip(final String region, final String vipId) {
    handleRequest {
      getRegionClient(region).networking().port().list()?.find { it.name == "vip-${vipId}".toString() }
    }
  }

  @Override
  NetFloatingIP getFloatingIpForPort(final String region, final String portId) {
    handleRequest {
      listNetFloatingIps(region)?.find { it.portId == portId }
    }
  }

  @Override
  List<NetFloatingIP> listNetFloatingIps(final String region) {
    handleRequest {
      getRegionClient(region).networking().floatingip().list()
    }
  }

}
