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

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import org.openstack4j.api.Builders
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.network.ext.HealthMonitorType
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbMethod
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.ListenerProtocol
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.model.network.ext.LoadBalancerV2StatusTree
import org.openstack4j.model.network.ext.Protocol

class OpenstackLoadBalancerV2Provider implements OpenstackLoadBalancerProvider, OpenstackRequestHandler, OpenstackIdentityAware {
  OpenstackIdentityProvider identityProvider

  OpenstackLoadBalancerV2Provider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider
  }

  @Override
  List<? extends LoadBalancerV2> getLoadBalancers(final String region) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().loadbalancer().list()
    }
  }

  @Override
  LoadBalancerV2 createLoadBalancer(final String region, final String name, final String description, final String subnetId) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().loadbalancer().create(Builders.loadbalancerV2()
        .name(name)
        .description(description)
        .subnetId(subnetId)
        .build())
    }
  }

  @Override
  LoadBalancerV2 getLoadBalancer(final String region, final String id) {
    LoadBalancerV2 result = handleRequest {
      getRegionClient(region).networking().lbaasV2().loadbalancer().get(id)
    }

    if (!result) {
      throw new OpenstackResourceNotFoundException("Unable to find load balancer ${id} in ${region}")
    }
    result
  }

  @Override
  ActionResponse deleteLoadBalancer(String region, String id) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().loadbalancer().delete(id)
    }
  }

  @Override
  LoadBalancerV2 getLoadBalancerByName(final String region, final String name) {
    handleRequest {
      List<? extends LoadBalancerV2> lbs = getRegionClient(region).networking().lbaasV2().loadbalancer().list(['name':name])
      lbs.size() > 0 ? lbs.first() : null
    }
  }

  @Override
  List<? extends ListenerV2> getListeners(final String region) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().listener().list()
    }
  }

  @Override
  ListenerV2 createListener(final String region, final String name, final String externalProtocol, final Integer externalPort, final String description, final String loadBalancerId) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().listener().create(Builders.listenerV2()
        .name(name)
        .description(description)
        .loadBalancerId(loadBalancerId)
        .protocolPort(externalPort)
        .protocol(ListenerProtocol.forValue(externalProtocol))
        .adminStateUp(Boolean.TRUE)
        .build())
    }
  }

  @Override
  ListenerV2 getListener(final String region, final String id) {
    ListenerV2 result = handleRequest {
      getRegionClient(region).networking().lbaasV2().listener().get(id)
    }

    if (!result) {
      throw new OpenstackResourceNotFoundException("Unable to find listener ${id} in ${region}")
    }
    result
  }

  @Override
  ActionResponse deleteListener(final String region, final String id) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().listener().delete(id)
    }
  }

  @Override
  List<? extends LbPoolV2> getPools(final String region) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().lbPool().list()
    }
  }

  @Override
  LbPoolV2 createPool(final String region, final String name, final String internalProtocol, final String method, final String listenerId) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().lbPool().create(Builders.lbpoolV2()
        .name(name)
        .lbMethod(LbMethod.forValue(method))
        .listenerId(listenerId)
        .protocol(Protocol.forValue(internalProtocol))
        .adminStateUp(Boolean.TRUE)
        .build())
    }
  }

  @Override
  LbPoolV2 getPool(final String region, final String id) {
    LbPoolV2 result = handleRequest {
      getRegionClient(region).networking().lbaasV2().lbPool().get(id)
    }

    if (!result) {
      throw new OpenstackResourceNotFoundException("Unable to find pool ${id} in ${region}")
    }
    result
  }

  @Override
  LbPoolV2 updatePool(final String region, final String id, final String method) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().lbPool().update(id, Builders.lbPoolV2Update()
        .lbMethod(LbMethod.forValue(method))
        .adminStateUp(Boolean.TRUE)
        .build())
    }
  }

  @Override
  ActionResponse deletePool(final String region, final String id) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().lbPool().delete(id)
    }
  }

  @Override
  List<? extends HealthMonitorV2> getHealthMonitors(final String region) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().healthMonitor().list()
    }
  }

  @Override
  ActionResponse deleteMonitor(final String region, final String id) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().healthMonitor().delete(id)
    }
  }

  @Override
  HealthMonitorV2 getMonitor(final String region, final String id) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().healthMonitor().get(id)
    }
  }

  @Override
  HealthMonitorV2 createMonitor(final String region, final String poolId, final HealthMonitor healthMonitor) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().healthMonitor().create(Builders.healthmonitorV2()
        .poolId(poolId)
        .type(HealthMonitorType.forValue(healthMonitor.type?.name()))
        .delay(healthMonitor.delay)
        .expectedCodes(healthMonitor.expectedCodes?.join(','))
        .httpMethod(healthMonitor.httpMethod)
        .maxRetries(healthMonitor.maxRetries)
        .timeout(healthMonitor.timeout)
        .urlPath(healthMonitor.url)
        .adminStateUp(Boolean.TRUE)
        .build())
    }
  }

  @Override
  HealthMonitorV2 updateMonitor(final String region, final String id, final HealthMonitor healthMonitor) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().healthMonitor().update(id, Builders.healthMonitorV2Update()
        .delay(healthMonitor.delay)
        .expectedCodes(healthMonitor.expectedCodes?.join(','))
        .httpMethod(healthMonitor.httpMethod)
        .maxRetries(healthMonitor.maxRetries)
        .timeout(healthMonitor.timeout)
        .urlPath(healthMonitor.url)
        .adminStateUp(Boolean.TRUE)
        .build())
    }
  }

  LoadBalancerV2StatusTree getLoadBalancerStatusTree(final String region, final String id) {
    handleRequest {
      getRegionClient(region).networking().lbaasV2().loadbalancer().statusTree(id)
    }
  }
}
