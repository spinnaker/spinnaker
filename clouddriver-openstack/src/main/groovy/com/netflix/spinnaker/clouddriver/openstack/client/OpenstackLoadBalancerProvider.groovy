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

import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2

/**
 * Operations associated to load balancer and relevant building blocks.
 */
interface OpenstackLoadBalancerProvider {
  /**
   * Creates new openstack load balancer.
   * @param region
   * @param name
   * @param description
   * @param subnetId
   * @return
   */
  LoadBalancerV2 createLoadBalancer(final String region, final String name, final String description, final String subnetId)

  /**
   * Retreives load balancer by id.
   * @param region
   * @param id
   * @return
   */
  LoadBalancerV2 getLoadBalancer(final String region, final String id)

  /**
   * Creates listener associated to an existing load balancer.
   * @param region
   * @param name
   * @param externalProtocol
   * @param externalPort
   * @param description
   * @param loadBalancerId
   * @return
   */
  ListenerV2 createListener(final String region, final String name, final String externalProtocol, final Integer externalPort, final String description, final String loadBalancerId)

  /**
   * Retreives listener by id.
   * @param region
   * @param id
   * @return
   */
  ListenerV2 getListener(final String region, final String id)

  /**
   * Removes listener by id.
   * @param region
   * @param id
   * @return
   */
  ActionResponse deleteListener(final String region, final String id)

  /**
   * Creates load balancer pool for a given listener.
   * @param region
   * @param name
   * @param internalProtocol
   * @param algorithm
   * @param listenerId
   * @return
   */
  LbPoolV2 createPool(final String region, final String name, final String internalProtocol, final String algorithm, final String listenerId)

  /**
   * Retreives pool by id.
   * @param region
   * @param id
   * @return
   */
  LbPoolV2 getPool(final String region, final String id)

  /**
   * Updates pool by id.
   * @param region
   * @param id
   * @param method
   * @return
   */
  LbPoolV2 updatePool(final String region, final String id, final String method)

  /**
   * Removes pool by id.
   * @param region
   * @param lbPoolId
   * @return
   */
  ActionResponse deletePool(final String region, final String lbPoolId)

  /**
   * Creates monitor for an existing pool.
   * @param region
   * @param poolId
   * @param healthMonitor
   * @return
   */
  HealthMonitorV2 createMonitor(final String region, final String poolId, final HealthMonitor healthMonitor)

  /**
   * Retreives monitor by id.
   * @param region
   * @param id
   * @return
   */
  HealthMonitorV2 getMonitor(final String region, final String id)

  /**
   * Updates monitor by id.
   * @param region
   * @param id
   * @param healthMonitor
   * @return
   */
  HealthMonitorV2 updateMonitor(final String region, final String id, final HealthMonitor healthMonitor)

  /**
   * Removes monitor by id.
   * @param region
   * @param id
   * @return
   */
  ActionResponse deleteMonitor(final String region, final String id)
}
