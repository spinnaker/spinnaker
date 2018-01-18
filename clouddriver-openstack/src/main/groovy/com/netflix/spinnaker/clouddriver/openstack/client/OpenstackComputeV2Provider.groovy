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
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import org.apache.commons.lang.StringUtils
import org.openstack4j.api.Builders
import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Flavor
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.RebootType
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.model.compute.Server
import org.openstack4j.model.compute.ext.AvailabilityZone

class OpenstackComputeV2Provider implements OpenstackComputeProvider, OpenstackRequestHandler, OpenstackIdentityAware {

  OpenstackIdentityProvider identityProvider

  OpenstackComputeV2Provider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider
  }

  @Override
  List<? extends AvailabilityZone> getZones(String region) {
    handleRequest {
      getRegionClient(region).compute().zones().list()
    }
  }

  @Override
  List<? extends Server> getInstances(String region) {
    handleRequest {
      getRegionClient(region).compute().servers().list()
    }
  }

  @Override
  String getConsoleOutput(String region, String serverId) {
    handleRequest {
      getRegionClient(region).compute().servers().getConsoleOutput(serverId, -1)
    }
  }

  @Override
  void deleteInstance(String region, String instanceId) {
    handleRequest {
      getRegionClient(region).compute().servers().delete(instanceId)
    }
  }

  @Override
  void rebootInstance(String region, String instanceId, RebootType rebootType) {
    handleRequest {
      getRegionClient(region).compute().servers().reboot(instanceId, rebootType)
    }
  }

  @Override
  void rebootInstance(String region, String instanceId) {
    handleRequest {
      getRegionClient(region).compute().servers().reboot(instanceId,  RebootType.SOFT)
    }
  }

  @Override
  FloatingIP getOrCreateFloatingIp(final String region, final String networkName) {
    handleRequest {
      FloatingIP ip = listFloatingIps(region).find { !it.fixedIpAddress }
      if (!ip) {
        ip = client.useRegion(region).compute().floatingIps().allocateIP(networkName)
        if (!ip) {
          throw new OpenstackProviderException("Unable to allocate new IP address on network $networkName")
        }
      }
      ip
    }
  }

  @Override
  List<? extends FloatingIP> listFloatingIps(final String region) {
    handleRequest {
      getRegionClient(region).compute().floatingIps().list()
    }
  }

  @Override
  void deleteSecurityGroup(String region, String securityGroupId) {
    handleRequest {
      getRegionClient(region).compute().securityGroups().delete(securityGroupId)
    }
  }

  @Override
  void deleteSecurityGroupRule(String region, String id) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().deleteRule(id)
    }
  }

  @Override
  SecGroupExtension.Rule createSecurityGroupRule(String region,
                                                 String securityGroupId,
                                                 IPProtocol protocol,
                                                 String cidr,
                                                 String remoteSecurityGroupId,
                                                 Integer fromPort,
                                                 Integer toPort,
                                                 Integer icmpType,
                                                 Integer icmpCode) {

    def builder = Builders.secGroupRule()
      .parentGroupId(securityGroupId)
      .protocol(protocol)

    /*
     * Openstack/Openstack4J overload the port range to indicate ICMP type and code. This isn't immediately
     * obvious and was found through testing and inferring things from the Openstack documentation.
     */
    if (protocol == IPProtocol.ICMP) {
      builder.range(icmpType, icmpCode)
    } else {
      builder.range(fromPort, toPort)
    }

    if (remoteSecurityGroupId) {
      builder.groupId(remoteSecurityGroupId)
    } else {
      builder.cidr(cidr)
    }

    handleRequest {
      client.useRegion(region).compute().securityGroups().createRule(builder.build())
    }
  }

  @Override
  SecGroupExtension updateSecurityGroup(String region, String id, String name, String description) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().update(id, name, description)
    }
  }

  @Override
  SecGroupExtension createSecurityGroup(String region, String name, String description) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().create(name, description)
    }
  }

  @Override
  SecGroupExtension getSecurityGroup(String region, String id) {
    SecGroupExtension securityGroup = handleRequest {
      client.useRegion(region).compute().securityGroups().get(id)
    }
    if (!securityGroup) {
      throw new OpenstackResourceNotFoundException("Unable to find security group ${id}")
    }
    securityGroup
  }

  @Override
  List<SecGroupExtension> getSecurityGroups(String region) {
    handleRequest {
      getRegionClient(region).compute().securityGroups().list()
    }
  }

  @Override
  Server getServerInstance(String region, String instanceId) {
    Server server = handleRequest {
      client.useRegion(region).compute().servers().get(instanceId)
    }
    if (!server) {
      throw new OpenstackProviderException("Could not find server with id ${instanceId}")
    }
    server
  }

  @Override
  List<? extends Flavor> listFlavors(String region) {
    handleRequest {
      this.getRegionClient(region).compute().flavors().list()
    }
  }

  @Override
  String getIpForInstance(String region, String instanceId) {
    Server server = getServerInstance(region, instanceId)
    /* TODO
      For now just get the first ipv4 address found. Openstack does not associate an instance id
      with load balancer membership, just an ip address. An instance can have multiple IP addresses.
      perhaps we just look for the first 192.* address found. It would also help to know the network name
      from which to choose the IP list. I am not sure if we will have that. We can certainly add that into
      the api later on when we know what data deck will have access to.
    */
    String ip = server.addresses?.addresses?.collect { n -> n.value }?.find()?.find { it.version == 4 }?.addr
    if (StringUtils.isEmpty(ip)) {
      throw new OpenstackProviderException("Instance ${instanceId} has no IP address")
    }
    ip
  }

  @Override
  List<? extends Address> getIpsForInstance(String region, String instanceId) {
    Server server = getServerInstance(region, instanceId)
    server.addresses?.addresses?.collect { n -> n.value }?.flatten()
  }

}
