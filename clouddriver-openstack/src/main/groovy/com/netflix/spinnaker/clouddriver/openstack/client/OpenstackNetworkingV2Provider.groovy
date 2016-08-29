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
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerResolver
import org.openstack4j.api.Builders
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.builder.PortBuilder

class OpenstackNetworkingV2Provider implements OpenstackNetworkingProvider, OpenstackRequestHandler, OpenstackIdentityAware, LoadBalancerResolver {

  OpenstackIdentityProvider identityProvider

  OpenstackNetworkingV2Provider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider
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

  @Override
  Port getPort(final String region, final String portId) {
    handleRequest {
      getRegionClient(region).networking().port().get(portId)
    }
  }

  @Override
  Port updatePort(final String region, final String portId, final List<String> securityGroups) {
    handleRequest {
      // Builder doesn't take in list of security groups and doesn't allow you to set the ID so, adding some ugly code :)
      PortBuilder portBuilder = Builders.port()
      securityGroups.each { portBuilder = portBuilder.securityGroup(it) }
      Port changedPort = portBuilder.build()
      changedPort.setId(portId)
      getRegionClient(region).networking().port().update(changedPort)
    }
  }

  @Override
  NetFloatingIP associateFloatingIpToPort(final String region, final String floatingIpId, final String portId) {
    handleRequest {
      getRegionClient(region).networking().floatingip().associateToPort(floatingIpId, portId)
    }
  }

  @Override
  NetFloatingIP disassociateFloatingIpFromPort(final String region, final String floatingIpId) {
    handleRequest {
      getRegionClient(region).networking().floatingip().disassociateFromPort(floatingIpId)
    }
  }
}
