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
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.LbaasConfig
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Algorithm
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Listener
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.MemberData
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.StackPoolMemberAware
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup.ServerGroupConstants
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerResolver
import com.netflix.spinnaker.clouddriver.openstack.task.TaskStatusAware
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.LbProvisioningStatus
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.openstack.networking.domain.ext.ListItem


class UpsertOpenstackLoadBalancerAtomicOperation implements AtomicOperation<Map>, TaskStatusAware, LoadBalancerResolver, StackPoolMemberAware {
  OpenstackLoadBalancerDescription description

  UpsertOpenstackLoadBalancerAtomicOperation(OpenstackLoadBalancerDescription description) {
    this.description = description
  }

  /*
   * Create:
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "upsertLoadBalancer": { "region": "RegionOne", "account": "test", "name": "stack-test", "subnetId": "8802895b-c46f-4074-b494-0a992b38e8c5", "networkId": "bcfdcd2f-57ec-4153-b145-139c81fa698e", "algorithm": "ROUND_ROBIN", "securityGroups": ["3c213029-f4f1-46ad-823b-d27dead4bf3f"], "healthMonitor": { "type": "PING", "delay": 10, "timeout": 10, "maxRetries": 10 }, "listeners": [ { "externalPort": 80, "externalProtocol":"HTTP", "internalPort": 8181, "internalProtocol": "HTTP" }] } } ]' localhost:7002/openstack/ops
   *
   * Update:
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "upsertLoadBalancer": { "region": "RegionOne", "account": "test", "id": "413910e0-ec00-448a-9427-228450c78bf0", "name": "stack-test", "subnetId": "8802895b-c46f-4074-b494-0a992b38e8c5", "networkId": "bcfdcd2f-57ec-4153-b145-139c81fa698e", "algorithm": "ROUND_ROBIN", "securityGroups": ["3c213029-f4f1-46ad-823b-d27dead4bf3f"], "healthMonitor": { "type": "PING", "delay": 10, "timeout": 10, "maxRetries": 10 }, "listeners": [ { "externalPort": 80, "externalProtocol":"HTTP", "internalPort": 8282, "internalProtocol": "HTTP" }] } } ]' localhost:7002/openstack/ops
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Initializing upsert of load balancer ${description.id ?: description.name} in ${description.region}..."

    String region = description.region
    LoadBalancerV2 resultLoadBalancer

    try {
      if (!this.description.id) {
        resultLoadBalancer = createLoadBalancer(region, description.name, description.subnetId)
      } else {
        resultLoadBalancer = clientProvider.getLoadBalancer(region, description.id)
      }

      Map<String, ListenerV2> existingListenerMap = buildListenerMap(region, resultLoadBalancer)
      Map<String, Listener> listenerMap = description.listeners.collectEntries([:]) { Listener current ->
        [(getListenerKey(current.externalPort, current.externalProtocol.name(), current.internalPort, current.internalProtocol.name())): current]
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
        removeListenersAndPools(region, resultLoadBalancer.id, deleteValues)
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
        updateStacks(region, resultLoadBalancer.id)
      }
    } catch (OpenstackProviderException ope) {
      throw new OpenstackOperationException(AtomicOperations.UPSERT_LOAD_BALANCER, ope)
    }

    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Done upserting load balancer ${resultLoadBalancer?.name} in ${region}"
    return [(region): [id: resultLoadBalancer?.id]]
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
    String description = generateCreatedTime(System.currentTimeMillis())
    LoadBalancerV2 result = createBlockingStatusChecker(region).execute { clientProvider.createLoadBalancer(region, name, description, subnetId) }
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created load balancer $name in ${region}."
    result
  }

  /**
   * Creates a list of MemberData (pool id && internalPort combination) associated to a given load balancer.  This is to recreate
   * pool member templates so that pools can be rebalanced with changes from upsert/create operations.
   * @param region
   * @param loadBalancerId
   * @param subnetId
   * @return
   */
  protected List<MemberData> buildMemberData(String region, List<String> loadBalancerIds, String subnetId) {
    List<MemberData> memberDataList = []
    loadBalancerIds?.each {
      LoadBalancerV2 loadBalancer = clientProvider.getLoadBalancer(region, it)
      loadBalancer.listeners.each {
        ListenerV2 listener = clientProvider.getListener(region, it.id)
        Map<String, String> listenerMap = parseListenerKey(listener.description)
        if (!listenerMap.isEmpty()) {
          memberDataList << new MemberData(poolId: listener.defaultPoolId, internalPort: listenerMap.get('internalPort'), subnetId: subnetId)
        }
      }
    }
    memberDataList
  }

  /**
   * Finds all of the stacks/server groups that the given load balancer is associated with and updates the stack defintion
   * so pool members will be added/removed with create/upsert changes.
   * @param region
   * @param loadBalancerId
   * @param memberDataList
   */
  protected void updateStacks(String region, String loadBalancerId) {
    //TODO - Refactor to use stack tags to filter by load balancer id.
    clientProvider.listStacks(region).each {
      Stack stack = clientProvider.getStack(region, it.name)
      ServerGroupParameters serverGroupParameters = ServerGroupParameters.fromParamsMap(stack.parameters)
      if (serverGroupParameters.loadBalancers?.contains(loadBalancerId)) {
        List<Map<String, Object>> outputs = stack.outputs
        String subtemplate = outputs.find { m -> m.get("output_key") == ServerGroupConstants.SUBTEMPLATE_OUTPUT }.get("output_value")
        List<MemberData> memberDataList = buildMemberData(region, serverGroupParameters.loadBalancers, description.subnetId)
        String memberTemplate = buildPoolMemberTemplate(memberDataList)

        //get the current template from the stack
        String template = clientProvider.getHeatTemplate(region, stack.name, stack.id)

        //update stack
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating stack ${stack.name} with member data ${memberDataList} in ${region}..."
        clientProvider.updateStack(region, stack.name, stack.id, template, [(ServerGroupConstants.SUBTEMPLATE_FILE): subtemplate, (ServerGroupConstants.MEMBERTEMPLATE_FILE): memberTemplate], serverGroupParameters)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated stack ${stack.name} with member data ${memberDataList} in ${region}."
      }
    }
  }

  /**
   * Updates security groups to vip port associated to load balancer.
   * @param region
   * @param portId
   * @param securityGroups
   */
  protected void updateSecurityGroups(String region, String portId, List<String> securityGroups) {
    Port port = clientProvider.getPort(region, portId)
    if (securityGroups && port.securityGroups != securityGroups) {
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating port ${portId} with security groups ${securityGroups} in ${region}..."
      clientProvider.updatePort(region, portId, securityGroups)
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
    NetFloatingIP existingFloatingIp = clientProvider.getFloatingIpForPort(region, portId)
    if (networkId) {
      Network network = clientProvider.getNetwork(region, networkId)
      FloatingIP ip = clientProvider.getOrCreateFloatingIp(region, network.name)
      if (!existingFloatingIp) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Associating floating IP ${ip.floatingIpAddress} with ${portId}..."
        clientProvider.associateFloatingIpToPort(region, ip.id, portId)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Associated floating IP ${ip.floatingIpAddress} with ${portId}."
      } else {
        if (networkId != existingFloatingIp.floatingNetworkId) {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociating ip ${existingFloatingIp.floatingIpAddress} and associating ip ${ip.floatingIpAddress} with vip ${portId}..."
          clientProvider.disassociateFloatingIpFromPort(region, existingFloatingIp.id)
          clientProvider.associateFloatingIpToPort(region, ip.id, portId)
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociated ip ${existingFloatingIp.id} and associated ip ${ip.floatingIpAddress} with vip ${portId}."
        }
      }
    } else {
      if (existingFloatingIp) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociating ip ${existingFloatingIp.floatingIpAddress} with vip ${portId}..."
        clientProvider.disassociateFloatingIpFromPort(region, existingFloatingIp.id)
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Disassociated ip ${existingFloatingIp.floatingIpAddress} with vip ${portId}."
      }
    }
  }

  /**
   * Removes load balancer listeners and pools associated with load balancer.
   * @param region
   * @param loadBalancerId
   * @param listeners
   */
  protected void removeListenersAndPools(String region, String loadBalancerId, Collection<ListenerV2> listeners) {
    BlockingStatusChecker pollingClientAdaptor = createBlockingStatusChecker(region, loadBalancerId)
    listeners?.each { ListenerV2 currentListener ->
      if (currentListener.defaultPoolId) {
        LbPoolV2 lbPool = pollingClientAdaptor.execute { clientProvider.getPool(region, currentListener.defaultPoolId) }
        if (lbPool.healthMonitorId) {
          removeHealthMonitor(region, loadBalancerId, lbPool.healthMonitorId)
        }
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removing pool ${currentListener.defaultPoolId} in ${region}"
        pollingClientAdaptor.execute { clientProvider.deletePool(region, currentListener.defaultPoolId) }
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removed pool ${currentListener.defaultPoolId} in ${region}"
      }
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removing listener ${currentListener.name} in ${region}"
      pollingClientAdaptor.execute { clientProvider.deleteListener(region, currentListener.id) }
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removed listener ${currentListener.name} in ${region}"
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
    BlockingStatusChecker pollingClientAdaptor = createBlockingStatusChecker(region, loadBalancerId)
    listeners?.each { String key, Listener currentListener ->
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating listener $name in ${region}"
      ListenerV2 listener = pollingClientAdaptor.execute {
        clientProvider.createListener(region, name, currentListener.externalProtocol.name(), currentListener.externalPort, key, loadBalancerId)
      }
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created listener $name in ${region}"
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating pool $name in ${region}"
      LbPoolV2 lbPool = pollingClientAdaptor.execute {
        clientProvider.createPool(region, name, currentListener.internalProtocol.name(), algorithm.name(), listener.id)
      }
      task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created pool $name in ${region}"
      updateHealthMonitor(region, loadBalancerId, lbPool, healthMonitor)
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
    BlockingStatusChecker pollingClientAdaptor = createBlockingStatusChecker(region, loadBalancerId)
    listeners?.each { ListenerV2 currentListener ->
      LbPoolV2 lbPool = null

      if (currentListener.defaultPoolId) {
        lbPool = clientProvider.getPool(region, currentListener.defaultPoolId)
        if (lbPool.lbMethod.name() != algorithm.name()) {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating pool $lbPool.name in ${region} ..."
          pollingClientAdaptor.execute { clientProvider.updatePool(region, lbPool.id, algorithm.name()) }
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated pool $lbPool.name in ${region}."
        }
      } else {
        Map<String, String> listenerMap = parseListenerKey(currentListener.description)
        if (!listenerMap?.isEmpty()) {
          // Create a pool
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating pool $currentListener.name in ${region} ..."
          lbPool = pollingClientAdaptor.execute { clientProvider.createPool(region, currentListener.name, listenerMap.internalProtocol, algorithm.name(), currentListener.id) }
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created pool $currentListener.name in ${region}."
        }
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
    BlockingStatusChecker pollingClientAdaptor = createBlockingStatusChecker(region, loadBalancerId)
    if (lbPool.healthMonitorId) {
      if (healthMonitor) {
        HealthMonitorV2 existingMonitor = clientProvider.getMonitor(region, lbPool.healthMonitorId)
        if (existingMonitor.type.name() == healthMonitor.type.name()) {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updating health monitor $lbPool.name in ${region} ..."
          pollingClientAdaptor.execute {
            clientProvider.updateMonitor(region, lbPool.healthMonitorId, healthMonitor)
          }
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Updated health monitor $lbPool.name in ${region}."
        } else {
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removing existing monitor ${existingMonitor.id} and creating health monitor for ${lbPool.name} in ${region}..."
          pollingClientAdaptor.execute { clientProvider.deleteMonitor(region, lbPool.healthMonitorId) }
          pollingClientAdaptor.execute { clientProvider.createMonitor(region, lbPool.id, healthMonitor) }
          task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removed existing monitor ${existingMonitor.id} and created health monitor for ${lbPool.name} in ${region}."
        }
      } else {
        removeHealthMonitor(region, loadBalancerId, lbPool.healthMonitorId)
      }
    } else {
      if (healthMonitor) {
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Creating health monitor for pool $lbPool.name in ${region} ..."
        pollingClientAdaptor.execute { clientProvider.createMonitor(region, lbPool.id, healthMonitor) }
        task.updateStatus UPSERT_LOADBALANCER_PHASE, "Created health monitor for pool $lbPool.name in ${region}."
      }
    }
  }

  /**
   * Shared method to remove a health monitor given its ID.
   * @param region
   * @param id
     */
  protected void removeHealthMonitor(String region, String loadBalancerId, String id) {
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removing existing monitor ${id} in ${region}..."
    createBlockingStatusChecker(region, loadBalancerId).execute { clientProvider.deleteMonitor(region, id) }
    task.updateStatus UPSERT_LOADBALANCER_PHASE, "Removed existing monitor ${id} in ${region}."
  }

  /**
   * Creates and returns a new polling client adaptor.
   * @param region
   * @param loadBalancerId
   * @return
     */
  protected BlockingStatusChecker createBlockingStatusChecker(String region, String loadBalancerId = null) {
    LbaasConfig lbaasConfig = this.description.credentials.credentials.lbaasConfig
    BlockingStatusChecker.from (lbaasConfig.pollTimeout, lbaasConfig.pollInterval) { Object input ->
      String id = loadBalancerId
      if (!loadBalancerId && input && input instanceof LoadBalancerV2) {
        id = ((LoadBalancerV2)input).id
      }
      LbProvisioningStatus currentProvisioningStatus = clientProvider.getLoadBalancer(region, id)?.provisioningStatus

      // Short circuit polling if openstack is unable to provision the load balancer
      if (LbProvisioningStatus.ERROR == currentProvisioningStatus) {
        throw new OpenstackProviderException("Openstack was unable to provision load balancer ${id}")
      }

      LbProvisioningStatus.ACTIVE == currentProvisioningStatus
    }
  }

  /**
   * Helper method to lookup listeners associated to load balancers into a map by listener key.
   * @param region
   * @param loadBalancer
   * @return
   */
  protected Map<String, ListenerV2> buildListenerMap(String region, LoadBalancerV2 loadBalancer) {
    loadBalancer?.listeners?.collectEntries([:]) { ListItem item ->
      ListenerV2 listenerV2 = clientProvider.getListener(region, item.id)
      [(listenerV2.description): listenerV2]
    }
  }

  /**
   * Helper method to DRY up provider access.
   * @return
   */
  OpenstackClientProvider getClientProvider() {
    this.description.credentials.provider
  }
}
