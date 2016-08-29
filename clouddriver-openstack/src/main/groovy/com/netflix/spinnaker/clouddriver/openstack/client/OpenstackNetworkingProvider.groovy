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

import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet

interface OpenstackNetworkingProvider {

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

  /**
   * Retreives port by id.
   * @param region
   * @param portId
   * @return
   */
  Port getPort(final String region, final String portId)

  /**
   * Updates port by id.
   * @param region
   * @param portId
   * @param securityGroups
   * @return
   */
  Port updatePort(final String region, final String portId, final List<String> securityGroups)

  /**
   * Associates floating ip address to port.
   * @param region
   * @param floatingIpId
   * @param portId
   * @return
   */
  NetFloatingIP associateFloatingIpToPort(final String region, final String floatingIpId, final String portId)

  /**
   * Disassociates floating ip address from port.
   * @param region
   * @param floatingIpId
   * @return
   */
  NetFloatingIP disassociateFloatingIpFromPort(final String region, final String floatingIpId)
}
