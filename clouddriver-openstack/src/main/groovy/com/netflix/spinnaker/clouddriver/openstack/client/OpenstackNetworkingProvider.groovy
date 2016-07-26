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

import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerPool
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.VirtualIP
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Member
import org.openstack4j.model.network.ext.Vip

interface OpenstackNetworkingProvider {

  /**
   * Get all Load Balancer Pools for region
   * @param region
   * @return List < ? extends LbPool >
   */
  List<? extends LbPool> getAllLoadBalancerPools(final String region)

  /**
   * Gets load balancer pool for a given region by load balancer UUID.
   * @param region
   * @param loadBalancerId
   * @return
   */
  LbPool getLoadBalancerPool(final String region, final String loadBalancerId)

  /**
   * Get all vips in a region.
   * @param region
   * @return
   */
  List<? extends Vip> listVips(final String region)

  /**
   * Gets VIP for a given region.
   * @param region
   * @param vipId
   * @return
   */
  Vip getVip(final String region, final String vipId)

  /**
   * Creates load balancer pool in provided region.
   * @param region
   * @param loadBalancerPool
   * @return LbPool
   */
  LbPool createLoadBalancerPool(final String region, final LoadBalancerPool loadBalancerPool)

  /**
   * Updates existing load balancer pool's name or load balancer method.
   * @param region
   * @param loadBalancerPool
   * @return
   */
  LbPool updateLoadBalancerPool(final String region, final LoadBalancerPool loadBalancerPool)

  /**
   * Creates VIP for given region and vip.
   * @param region
   * @param virtualIP
   * @return
   */
  Vip createVip(final String region, final VirtualIP virtualIP)

  /**
   * Updates VIP in specified region.
   * @param region
   * @param virtualIP
   * @return
   */
  Vip updateVip(final String region, final VirtualIP virtualIP)

  /**
   * Gets HealthMonitor for given region and id.
   * @param region
   * @param healthMonitorId
   * @return
   */
  HealthMonitor getHealthMonitor(final String region, final String healthMonitorId)

  /**
   * Creates health check for given pool in specified region.
   * @param region
   * @param lbPoolId
   * @param monitor
   * @return
   */
  HealthMonitor createHealthCheckForPool(final String region, final String lbPoolId, final PoolHealthMonitor monitor)

  /**
   * Updates health monitor for a given region.
   * @param region
   * @param monitor
   * @return
   */
  HealthMonitor updateHealthMonitor(final String region, final PoolHealthMonitor monitor)

  /**
   * Disassociates and removes health monitor from load balancer.
   * @param region
   * @param lbPoolId
   * @param healthMonitorId
   */
  void disassociateAndRemoveHealthMonitor(String region, String lbPoolId, String healthMonitorId)

  /**
   * Deletes a health monitor.
   * @param region
   * @param healthMonitorId
   * @return
   */
  ActionResponse deleteHealthMonitor(String region, String healthMonitorId)

  /**
   * Disassociates health monitor from loadbalancer pool.
   * @param region
   * @param lbPoolId
   * @param healMonitorId
   */
  ActionResponse disassociateHealthMonitor(String region, String lbPoolId, String healthMonitorId)

/**
 * Get a network from the network id.
 * @param region
 * @param networkId
 * @return
 */
  Network getNetwork(final String region, final String networkId)

  /**
   * Associate already known floating IP address to VIP in specified region.
   * @param region
   * @param floatingIpId
   * @param vipId
   * @return
   */
  NetFloatingIP associateFloatingIpToVip(final String region, final String floatingIpId, final String vipId)

  /**
   * Remove port associated with floating IP.
   * @param region
   * @param floatingIpId
   * @return
   */
  NetFloatingIP disassociateFloatingIp(final String region, final String floatingIpId)

  /**
   * List all ports in the region.
   * @param region
   * @return
   */
  List<? extends Port> listPorts(final String region)

  /**
   * Get port from load balancer description. Openstack load balancers have no native concept of internal port,
   * so we store in the description field of the load balancer.
   * this may be changed in a future version.
   * @param pool
   * @return
   */
  int getInternalLoadBalancerPort(LbPool pool)

  /**
   *
   * @param ip
   * @param lbPoolId
   * @param internalPort
   * @param weight
   */
  Member addMemberToLoadBalancerPool(String region, String ip, String lbPoolId, int internalPort, int weight)

  /**
   *
   * @param memberId
   * @return
   */
  ActionResponse removeMemberFromLoadBalancerPool(String region, String memberId)
  /**
   *
   * @param instanceId
   * @param ip
   * @param lbPool
   */
  String getMemberIdForInstance(String region, String ip, LbPool lbPool)

  /**
   * Remove load balancer vip.
   * @param region
   * @param poolId
   */
  void deleteVip(String region, String vipId)

  /**
   * Remove load balancer pool.
   * @param region
   * @param poolId
   */
  void deleteLoadBalancerPool(String region, String poolId)

  /**
   * List the available networks in a region. These will be both internal and external networks.
   * @param region
   * @return
   */
  List<Network> listNetworks(String region)

  /**
   * Returns a list of available subnets by region.
   * @param region
   * @return
   */
  List<Subnet> listSubnets(String region)

  /**
   * Get the subnet in a region.
   * @param region
   * @param subnetId
   * @return boolean
   */
  Subnet getSubnet(final String region, final String subnetId)

  /**
   * Internal helper to look up port associated to vip.
   * @param region
   * @param vipId
   * @return
   */
  Port getPortForVip(final String region, final String vipId)

  /**
   * Helper to get the floating ip bound to a port.
   * @param region
   * @param portId
   * @return
   */
  NetFloatingIP getFloatingIpForPort(final String region, final String portId)

  /**
   * List network floating ips.
   * @param region
   * @return
   */
  List<NetFloatingIP> listNetFloatingIps(final String region)
}
