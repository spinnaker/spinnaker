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

import com.google.common.collect.Sets
import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Algorithm
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Listener
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.task.TaskStatusAware
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2

class UpsertOpenstackLoadBalancerAtomicOperation extends AbstractOpenstackLoadBalancerAtomicOperation implements AtomicOperation<Map>, TaskStatusAware {
  OpenstackLoadBalancerDescription description

  UpsertOpenstackLoadBalancerAtomicOperation(OpenstackLoadBalancerDescription description) {
    super(description.credentials)
    this.description = description
  }

  /*
   * Create:
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "upsertLoadBalancer": { "region": "RegionOne", "account": "test", "name": "stack-test", "subnetId": "8802895b-c46f-4074-b494-0a992b38e8c5", "networkId": "bcfdcd2f-57ec-4153-b145-139c81fa698e", "algorithm": "ROUND_ROBIN", "securityGroups": ["3c213029-f4f1-46ad-823b-d27dead4bf3f"], "healthMonitor": { "type": "PING", "delay": 10, "timeout": 10, "maxRetries": 10 }, "listeners": [ { "externalPort": 80, "externalProtocol":"HTTP", "internalPort": 8181 }] } } ]' localhost:7002/openstack/ops
   *
   * Update:
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "upsertLoadBalancer": { "region": "RegionOne", "account": "test", "id": "413910e0-ec00-448a-9427-228450c78bf0", "name": "stack-test", "subnetId": "8802895b-c46f-4074-b494-0a992b38e8c5", "networkId": "bcfdcd2f-57ec-4153-b145-139c81fa698e", "algorithm": "ROUND_ROBIN", "securityGroups": ["3c213029-f4f1-46ad-823b-d27dead4bf3f"], "healthMonitor": { "type": "PING", "delay": 10, "timeout": 10, "maxRetries": 10 }, "listeners": [ { "externalPort": 80, "externalProtocol":"HTTP", "internalPort": 8282 }] } } ]' localhost:7002/openstack/ops
   */

  @Override
  Map operate(List priorOutputs) {
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Initializing upsert of load balancer ${description.id ?: description.name} in ${description.region}..."

    String region = description.region
    LoadBalancerV2 resultLoadBalancer

    try {
      if (!this.description.id) {
        validatePeripherals(region, description.subnetId, description.networkId, description.securityGroups)
        resultLoadBalancer = createLoadBalancer(region, description.name, description.subnetId)
      } else {
        resultLoadBalancer = provider.getLoadBalancer(region, description.id)
        if (!resultLoadBalancer) {
          throw new OpenstackResourceNotFoundException("Could not find load balancer: $description.id in region: $region")
        }
        checkPendingLoadBalancerState(resultLoadBalancer)
      }

      Map<String, ListenerV2> existingListenerMap = buildListenerMap(region, resultLoadBalancer)
      Map<String, Listener> listenerMap = description.listeners.collectEntries([:]) { Listener current ->
        [(getListenerKey(current.externalProtocol.name(), current.externalPort, current.internalPort)): current]
      }
      Map<String, ListenerV2> listenersToUpdate = [:]
      Map<String, Listener> listenersToAdd = [:]

      listenerMap.entrySet()?.each { Map.Entry<String, Listener> entry ->
        ListenerV2 foundListener = existingListenerMap.get(entry.key)
        if (foundListener) {
          listenersToUpdate.put(entry.key, foundListener)
        } else {
          listenersToAdd.put(entry.key, entry.value)
        }
      }

      Set<String> listenersToDelete = Sets.difference(existingListenerMap.keySet(), Sets.union(listenersToAdd.keySet(), listenersToUpdate.keySet()))

      if (listenersToDelete) {
        List<ListenerV2> deleteValues = existingListenerMap.findAll { listenersToDelete.contains(it.key) }.collect {
          it.value
        }
        deleteLoadBalancerPeripherals(UPSERT_LOADBALANCER_PHASE, region, resultLoadBalancer.id, deleteValues)
      }

      if (listenersToAdd) {
        addListenersAndPools(region, resultLoadBalancer.id, description.name, description.algorithm, listenersToAdd, description.healthMonitor)
      }

      if (listenersToUpdate) {
        updateListenersAndPools(region, resultLoadBalancer.id, description.algorithm, listenersToUpdate.values(), description.healthMonitor)
      }

      updateFloatingIp(region, description.networkId, resultLoadBalancer.vipPortId)
      updateSecurityGroups(region, resultLoadBalancer.vipPortId, description.securityGroups)

      // Add members to newly created pools through an existing stack only
      if (description.id && (!listenersToAdd.isEmpty() || !listenersToDelete.isEmpty())) {
        updateServerGroup(UPSERT_LOADBALANCER_PHASE, region, resultLoadBalancer.id)
      }
    } catch (OpenstackProviderException ope) {
      throw new OpenstackOperationException(AtomicOperations.UPSERT_LOAD_BALANCER, ope)
    }

    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Done upserting load balancer ${resultLoadBalancer?.name} in ${region}"
    return [(region): [id: resultLoadBalancer?.id]]
  }

  /**
   * Validates load balancer components subnet, network, and security groups are real.
   * @param region
   * @param subnetId
   * @param networkId
   * @param securityGroups
   */
  protected void validatePeripherals(String region, String subnetId, String networkId, List<String> securityGroups) {
    if (!provider.getSubnet(region, subnetId)) {
      throw new OpenstackResourceNotFoundException("Subnet provided is invalid ${subnetId}")
    }

    if (networkId && !provider.getNetwork(region, networkId)) {
      throw new OpenstackResourceNotFoundException("Network provided is invalid ${networkId}")
    }

    securityGroups?.each {
      if (!provider.getSecurityGroup(region, it)) {
        throw new OpenstackResourceNotFoundException("Could not find securityGroup: $it in region: $region")
      }
    }
  }
  /**
   * Creates a load balancer in given subnet.
   * @param region
   * @param name
   * @param subnetId
   * @return
   */
  protected LoadBalancerV2 createLoadBalancer(String region, String name, String subnetId) {
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating load balancer $name in ${region} ..."
    String createdTime = generateCreatedTime(System.currentTimeMillis())

    LoadBalancerV2 result = provider.createLoadBalancer(region, name, createdTime, subnetId)
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Waiting on creation of load balancer $name in ${region} ..."
    result = LoadBalancerChecker.from(description.credentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.CREATE).execute {
      provider.getLoadBalancer(region, result.id)
    }
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created load balancer $name in ${region}."
    result
  }

  /**
   * Updates security groups to vip port associated to load balancer.
   * @param region
   * @param portId
   * @param securityGroups
   */
  protected void updateSecurityGroups(String region, String portId, List<String> securityGroups) {
    Port port = provider.getPort(region, portId)
    if (securityGroups && port.securityGroups != securityGroups) {
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating port ${portId} with security groups ${securityGroups} in ${region}..."
      provider.updatePort(region, portId, securityGroups)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated port ${portId} with security groups ${securityGroups} in ${region}."
    }
  }

  /**
   * Adds/Removes floating ip address associated to load balancer.
   * @param region
   * @param networkId
   * @param portId
   */
  protected void updateFloatingIp(String region, String networkId, String portId) {
    NetFloatingIP existingFloatingIp = provider.getFloatingIpForPort(region, portId)
    if (networkId) {
      Network network = provider.getNetwork(region, networkId)
      FloatingIP ip = provider.getOrCreateFloatingIp(region, network.name)
      if (!existingFloatingIp) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Associating floating IP ${ip.floatingIpAddress} with ${portId}..."
        provider.associateFloatingIpToPort(region, ip.id, portId)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Associated floating IP ${ip.floatingIpAddress} with ${portId}."
      } else {
        if (networkId != existingFloatingIp.floatingNetworkId) {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociating ip ${existingFloatingIp.floatingIpAddress} and associating ip ${ip.floatingIpAddress} with vip ${portId}..."
          provider.disassociateFloatingIpFromPort(region, existingFloatingIp.id)
          provider.associateFloatingIpToPort(region, ip.id, portId)
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociated ip ${existingFloatingIp.id} and associated ip ${ip.floatingIpAddress} with vip ${portId}."
        }
      }
    } else {
      if (existingFloatingIp) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociating ip ${existingFloatingIp.floatingIpAddress} with vip ${portId}..."
        provider.disassociateFloatingIpFromPort(region, existingFloatingIp.id)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociated ip ${existingFloatingIp.floatingIpAddress} with vip ${portId}."
      }
    }
  }

  /**
   * Adds listeners and pools to an existing load balancer.
   * @param region
   * @param loadBalancerId
   * @param name
   * @param listeners
   * @return
   */
  protected void addListenersAndPools(String region, String loadBalancerId, String name, Algorithm algorithm, Map<String, Listener> listeners, HealthMonitor healthMonitor) {
    listeners?.each { String key, Listener currentListener ->
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating listener $name in ${region}"
      ListenerV2 listener = provider.createListener(region, name, currentListener.externalProtocol.name(), currentListener.externalPort, key, loadBalancerId)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Waiting on creation of listener $name in ${region}"
      LoadBalancerChecker.from(openstackCredentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.UPDATE).execute {
        provider.getLoadBalancer(region, loadBalancerId)
      }
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created listener $name in ${region}"

      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating pool $name in ${region}"
      LbPoolV2 pool = provider.createPool(region, name, currentListener.externalProtocol.internalProtocol, algorithm.name(), listener.id)
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Waiting on creation of pool $name in ${region}"
      LoadBalancerChecker.from(openstackCredentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.UPDATE).execute {
        provider.getLoadBalancer(region, loadBalancerId)
      }
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created pool $name in ${region}"
      updateHealthMonitor(region, loadBalancerId, pool, healthMonitor)
    }
  }

  /**
   * Updates existing load balancer listener and pool.
   * @param region
   * @param algorithm
   * @param loadBalancerId
   * @param listeners
   */
  protected void updateListenersAndPools(String region, String loadBalancerId, Algorithm algorithm, Collection<ListenerV2> listeners, HealthMonitor healthMonitor) {
    listeners?.each { ListenerV2 currentListener ->
      LbPoolV2 lbPool = provider.getPool(region, currentListener.defaultPoolId)
      if (lbPool.lbMethod.name() != algorithm.name()) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating pool $lbPool.name in ${region} ..."
        provider.updatePool(region, lbPool.id, algorithm.name())
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "waiting on update for pool $lbPool.name in ${region} ..."
        LoadBalancerChecker.from(openstackCredentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.UPDATE).execute {
          provider.getLoadBalancer(region, loadBalancerId)
        }
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated pool $lbPool.name in ${region}."
      }

      updateHealthMonitor(region, loadBalancerId, lbPool, healthMonitor)
    }
  }

  /**
   * Adds/Removes/Updates a health monitor to a given load balancer and pool.
   * @param region
   * @param loadBalancerId
   * @param lbPool
   */
  protected void updateHealthMonitor(String region, String loadBalancerId, LbPoolV2 lbPool, HealthMonitor healthMonitor) {
    if (lbPool.healthMonitorId) {
      if (healthMonitor) {
        HealthMonitorV2 existingMonitor = provider.getMonitor(region, lbPool.healthMonitorId)
        if (existingMonitor.type.name() == healthMonitor.type.name()) {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating health monitor $lbPool.name in ${region} ..."
          provider.updateMonitor(region, lbPool.healthMonitorId, healthMonitor)
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Waiting on update to health monitor $lbPool.name in ${region} ..."
          LoadBalancerChecker.from(openstackCredentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.UPDATE).execute {
            provider.getLoadBalancer(region, loadBalancerId)
          }
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated health monitor $lbPool.name in ${region}."
        } else {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removing existing monitor ${existingMonitor.id} and creating health monitor for ${lbPool.name} in ${region}..."
          provider.deleteMonitor(region, lbPool.healthMonitorId)
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Waiting on remove of existing monitor ${existingMonitor.id} in ${region}"
          LoadBalancerChecker.from(openstackCredentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.UPDATE).execute {
            provider.getLoadBalancer(region, loadBalancerId)
          }
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Waiting on creattion of health monitor for ${lbPool.name} in ${region}..."
          provider.createMonitor(region, lbPool.id, healthMonitor)
          LoadBalancerChecker.from(openstackCredentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.UPDATE).execute {
            provider.getLoadBalancer(region, loadBalancerId)
          }
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removed existing monitor ${existingMonitor.id} and created health monitor for ${lbPool.name} in ${region}."
        }
      } else {
        removeHealthMonitor(UPSERT_LOADBALANCER_PHASE, region, loadBalancerId, lbPool.healthMonitorId)
      }
    } else {
      if (healthMonitor) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating health monitor for pool $lbPool.name in ${region} ..."
        provider.createMonitor(region, lbPool.id, healthMonitor)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Waiting on creation of health monitor for pool $lbPool.name in ${region} ..."
        LoadBalancerChecker.from(openstackCredentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.UPDATE).execute {
          provider.getLoadBalancer(region, loadBalancerId)
        }
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created health monitor for pool $lbPool.name in ${region}."
      }
    }
  }
}
