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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerPool
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.VirtualIP
import com.netflix.spinnaker.clouddriver.openstack.task.TaskStatusAware
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Vip

class UpsertOpenstackLoadBalancerAtomicOperation implements AtomicOperation<Map>, TaskStatusAware {
  OpenstackLoadBalancerDescription description

  UpsertOpenstackLoadBalancerAtomicOperation(OpenstackLoadBalancerDescription description) {
    this.description = description
  }

  /*
   * Create:
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "upsertLoadBalancer": { "region": "region", "account": "test", "name": "test",  "protocol": "HTTP", "method" : "ROUND_ROBIN", "subnetId": "9e0d71a9-0086-494a-91d8-abad0912ba83", "externalPort": 80, "internalPort": 8100, "networkId": "08c2990a-b630-491c-9d1b-5534e25bc118", "healthMonitor": { "type": "PING", "delay": 10, "timeout": 10, "maxRetries": 10 } } } ]' localhost:7002/openstack/ops
   *
   * Update:
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "upsertLoadBalancer": { "region": "region", "id": "7d1b3734-5c29-4305-b124-c973516b83eb",  "account": "test", "method": "ROUND_ROBIN", "name": "test", "internalPort": 8181, "networkId": "08c2990a-b630-491c-9d1b-5534e25bc118", "healthMonitor": { "type": "PING", "delay": 1, "timeout": 1, "maxRetries": 1 } } } ]' localhost:7002/openstack/ops
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Initializing upsert of load balancer ${description.id ?: description.name} in ${description.region}..."
    String subnetId = description.subnetId
    String region = description.region
    LoadBalancerPool newLoadBalancerPool = new LoadBalancerPool(
      id: description.id,
      name: description.name,
      protocol: description.protocol,
      method: description.method,
      subnetId: subnetId,
      internalPort: description.internalPort)
    VirtualIP virtualIP = new VirtualIP(
      name: description.name,
      subnetId: subnetId,
      poolId: description.id,
      protocol: description.protocol,
      port: description.externalPort)
    PoolHealthMonitor poolHealthMonitor = description.healthMonitor

    LbPool resultPool
    try {
      if (newLoadBalancerPool.id) {
        resultPool = updateLoadBalancer(region, newLoadBalancerPool, virtualIP, poolHealthMonitor)
      } else {
        resultPool = createLoadBalancer(region, subnetId, newLoadBalancerPool, virtualIP, poolHealthMonitor)
      }
    } catch (OpenstackProviderException ope) {
      throw new OpenstackOperationException(AtomicOperations.UPSERT_LOAD_BALANCER, ope)
    }

    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Done upserting load balancer ${resultPool?.name} in ${region}"
    return [(region): [id: resultPool?.id]]
  }

  protected LbPool createLoadBalancer(String region, String subnetId, LoadBalancerPool newLoadBalancerPool, VirtualIP virtualIP, PoolHealthMonitor poolHealthMonitor) {
    LbPool resultPool
    OpenstackClientProvider openstackClientProvider = getClientProvider()

    if (!openstackClientProvider.getSubnet(region, subnetId)) {
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Unable to retrieve referenced subnet ${subnetId} in ${region}."
      throw new OpenstackOperationException(AtomicOperations.UPSERT_LOAD_BALANCER, "Subnet ${subnetId} not found in ${region}")
    }

    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating lbPool ${newLoadBalancerPool.name} in ${region}..."
    resultPool = openstackClientProvider.createLoadBalancerPool(region, newLoadBalancerPool)
    virtualIP.poolId = resultPool.id
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created lbPool ${newLoadBalancerPool.name} in ${region}"

    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating vip for lbPool ${resultPool.name} in ${region}..."
    Vip vip = openstackClientProvider.createVip(region, virtualIP)
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created vip for lbPool ${resultPool.name} in ${region} with name ${vip}."

    if (poolHealthMonitor) {
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating health checks for lbPool ${resultPool.name} in ${region}..."
      openstackClientProvider.createHealthCheckForPool(region, resultPool.id, poolHealthMonitor)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created health checks for lbPool ${resultPool.name} in ${region}."
    }

    if (description.networkId) {
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Obtaining network name from network id $description.networkId..."
      Network network = openstackClientProvider.getNetwork(region, description.networkId)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Successfully obtained network name from network id $description.networkId."

      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Obtaining floating IP from network $network.name..."
      FloatingIP ip = openstackClientProvider.getOrCreateFloatingIp(region, network.name)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Successfully obtained floating IP from network $network.name."

      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Associating floating IP ${ip.id} with ${vip.id}..."
      NetFloatingIP floatingIP = openstackClientProvider.associateFloatingIpToVip(region, ip.id, vip.id)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Associated floating IP ${floatingIP.floatingIpAddress} with ${vip.id}."
    }
    resultPool
  }

  protected LbPool updateLoadBalancer(String region, LoadBalancerPool loadBalancerPool, VirtualIP virtualIP, PoolHealthMonitor poolHealthMonitor) {
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating load balancer pool ${loadBalancerPool.id} in ${region}..."

    OpenstackClientProvider openstackClientProvider = getClientProvider()

    LbPool existingPool = getClientProvider().getLoadBalancerPool(region, loadBalancerPool.id)

    if (!loadBalancerPool.equals(existingPool)) {
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating lbPool ${loadBalancerPool.name} in ${region}..."
      openstackClientProvider.updateLoadBalancerPool(region, loadBalancerPool)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated lbPool ${loadBalancerPool.name} in ${region}."
    }

    Vip existingVip = openstackClientProvider.getVip(region, existingPool.vipId)
    if (!virtualIP.equals(existingVip)) {
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating vip ${virtualIP.name} in ${region}..."
      virtualIP.id = existingPool.vipId
      openstackClientProvider.updateVip(region, virtualIP)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated vip ${virtualIP.name} in ${region}."
    }

    // Currently only supporting one health check ... Could be extended to support multiple in future
    String healthMonitorId = existingPool.healthMonitors?.isEmpty() ? null : existingPool.healthMonitors?.first()
    if (poolHealthMonitor) {
      if (healthMonitorId) {
        HealthMonitor healthMonitor = openstackClientProvider.getHealthMonitor(region, healthMonitorId)
        if (healthMonitor.type.name().equals(poolHealthMonitor.type.name())) {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating health check ${healthMonitor.id} in ${region}..."
          // Set id and update
          poolHealthMonitor.id = healthMonitor.id
          openstackClientProvider.updateHealthMonitor(region, poolHealthMonitor)
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated health check ${healthMonitor.id} in ${region}."
        } else {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removing existing monitor ${healthMonitorId} and creating health check for lbPool ${existingPool.name} in ${region}..."
          // Types are different (i.e. PING vs HTTP) and can't be updated
          openstackClientProvider.disassociateAndRemoveHealthMonitor(region, existingPool.id, healthMonitorId)
          openstackClientProvider.createHealthCheckForPool(region, existingPool.id, poolHealthMonitor)
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removed existing monitor ${healthMonitorId} and created health check for lbPool ${existingPool.name} in ${region}."
        }
      } else {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating health check for lbPool ${existingPool.name} in ${region}..."
        openstackClientProvider.createHealthCheckForPool(region, existingPool.id, poolHealthMonitor)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created health check for lbPool ${existingPool.name} in ${region}."
      }
    } else {
      if (healthMonitorId) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removing existing monitor ${healthMonitorId} in ${region}..."
        openstackClientProvider.disassociateAndRemoveHealthMonitor(region, existingPool.id, healthMonitorId)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removed existing monitor ${healthMonitorId} in ${region}..."
      }
    }

    Port port = openstackClientProvider.getPortForVip(region, existingVip.id)
    NetFloatingIP existingFloatingIp = openstackClientProvider.getFloatingIpForPort(region, port.id)
    if (description.networkId) {
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Obtaining network name from network id $description.networkId..."
      Network network = openstackClientProvider.getNetwork(region, description.networkId)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Successfully obtained network name from network id $description.networkId..."

      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Obtaining floating IP from network $network.name..."
      FloatingIP ip = openstackClientProvider.getOrCreateFloatingIp(region, network.name)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Successfully obtained floating IP from network $network.name..."

      if (!existingFloatingIp) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Associating floating IP ${ip.id} with ${existingVip.id}..."
        openstackClientProvider.associateFloatingIpToVip(region, ip.id, existingVip.id)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Associated floating IP ${ip.id} with ${existingVip.id}."
      } else {
        if (ip.id != existingFloatingIp.id) {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociating ip ${existingFloatingIp.id} and associating ip ${ip.id} with vip ${existingVip.name}..."
          openstackClientProvider.disassociateFloatingIp(region, existingFloatingIp.id)
          openstackClientProvider.associateFloatingIpToVip(region, ip.id, existingVip.id)
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociated ip ${existingFloatingIp.id} and associated ip ${ip.id} with vip ${existingVip.name}."
        }
      }
    } else {
      if (existingFloatingIp) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociating ip ${existingFloatingIp.id} with vip ${existingVip.name}..."
        openstackClientProvider.disassociateFloatingIp(region, existingFloatingIp.id)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociated ip ${existingFloatingIp.id} with vip ${existingVip.name}."
      }
    }

    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated load balancer pool ${loadBalancerPool.id} in ${region}."
    existingPool
  }

  OpenstackClientProvider getClientProvider() {
    this.description.credentials.provider
  }
}
