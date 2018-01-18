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

import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Flavor
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.RebootType
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.model.compute.Server
import org.openstack4j.model.compute.ext.AvailabilityZone

/**
 * Methods for interacting with the current compute api.
 */
interface OpenstackComputeProvider {

  /**
   * Requests a list of the availability zones in a given region.
   * @param region
   * @return
   */
  List<? extends AvailabilityZone> getZones(String region)

  /**
   * Returns a list of instances in a given region.
   * @param region
   * @return
   */
  List<? extends Server> getInstances(String region)

  /**
   * Returns all of the console output for a given server and region.
   * @param region
   * @param serverId
   * @return
   */
  String getConsoleOutput(String region, String serverId)

  /**
   * Delete an instance.
   * @param instanceId
   * @return
   */
  void deleteInstance(String region, String instanceId)

  /**
   * Reboot an instance.
   * @param region
   * @param instanceId
   * @param rebootType
     */
  void rebootInstance(String region, String instanceId, RebootType rebootType)

  /**
   * Reboot an instance ... Default to SOFT reboot.
   * @param region
   * @param instanceId
     */
  void rebootInstance(String region, String instanceId)

  /**
   * Get an unallocated IP from the network, or if none are found, try to create a new floating IP in the network.
   * @param region
   * @param networkName
   * @return
   */
  FloatingIP getOrCreateFloatingIp(final String region, final String networkName)

  /**
   * List all floating ips in the region.
   * @param region
   * @return
   */
  List<? extends FloatingIP> listFloatingIps(final String region)

  /**
   * Deletes a security group.
   *
   * @param region the region the security group is in
   * @param securityGroupId id of the security group
   */
  void deleteSecurityGroup(String region, String securityGroupId)

  /**
   * Deletes a security group rule
   * @param region the region to delete the rule from
   * @param id id of the rule to delete
   */
  void deleteSecurityGroupRule(String region, String id)
  /**
   * Creates a security group rule.
   *
   * If the rule is for TCP or UDP, the fromPort and toPort are used. For ICMP rules, the imcpType and icmpCode are used instead.
   *
   * @param region the region to create the rule in
   * @param securityGroupId id of the security group which this rule belongs to
   * @param protocol the protocol of the rule
   * @param cidr the cidr for the rule
   * @param remoteSecurityGroupId id of security group referenced by this rule
   * @param fromPort the fromPort for the rule
   * @param toPort the toPort for the rule
   * @param icmpType the type of the ICMP control message
   * @param icmpCode the code or subtype of the ICMP control message
   * @return the created rule
   */
  SecGroupExtension.Rule createSecurityGroupRule(String region,
                                                 String securityGroupId,
                                                 IPProtocol protocol,
                                                 String cidr,
                                                 String remoteSecurityGroupId,
                                                 Integer fromPort,
                                                 Integer toPort,
                                                 Integer icmpType,
                                                 Integer icmpCode)

  /**
   * Updates a security group with the new name and description
   * @param region the region the security group is in
   * @param id the id of the security group to update
   * @param name the new name for the security group
   * @param description the new description for the security group
   * @return the updated security group
   */
  SecGroupExtension updateSecurityGroup(String region, String id, String name, String description)

  /**
   * Creates a security group with the given name and description
   * @return the created security group
   */
  SecGroupExtension createSecurityGroup(String region, String name, String description)

  /**
   * Returns the security group for the given id.
   * @param region the region to look up the security group in
   * @param id id of the security group.
   */
  SecGroupExtension getSecurityGroup(String region, String id)

  /**
   * Returns the list of all security groups for the given region
   */
  List<SecGroupExtension> getSecurityGroups(String region)

  /**
   * Get a compute server based on id.
   * @param instanceId
   * @return
   */
  Server getServerInstance(String region, String instanceId)

  /**
   * Returns a list of flavors by region.
   * @param region
   * @return
   */
  List<? extends Flavor> listFlavors(String region)

  /**
   * Get the first v4 IP address from a server.
   * @param server
   * @return
   */
  String getIpForInstance(String region, String instanceId)

  /**
   * Get all addresses for a server instance.
   * @param region
   * @param instanceId
   * @return
   */
  List<? extends Address> getIpsForInstance(String region, String instanceId)
}
