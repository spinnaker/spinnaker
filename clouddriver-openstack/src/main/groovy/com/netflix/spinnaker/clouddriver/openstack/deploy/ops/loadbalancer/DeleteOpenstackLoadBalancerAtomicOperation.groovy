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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.DeleteOpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import groovy.util.logging.Slf4j
import org.openstack4j.model.network.ext.LbPool

/**
 * Removes an openstack load balancer.
 */
@Slf4j
class DeleteOpenstackLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  private final String BASE_PHASE = 'DELETE_LOAD_BALANCER'
  DeleteOpenstackLoadBalancerDescription description

  DeleteOpenstackLoadBalancerAtomicOperation(DeleteOpenstackLoadBalancerDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
  * curl -X POST -H "Content-Type: application/json" -d  '[ {  "deleteLoadBalancer": { "id": "6adc02a8-7b01-4f90-9e6f-9a4c3411e7ad", "region": "default", "account":  "test" } } ]' localhost:7002/openstack/ops
  */

  @Override
  Void operate(List priorOutputs) {
    String region = description.region
    String loadBalancerId = description.id

    task.updateStatus BASE_PHASE, "Deleting load balancer ${loadBalancerId} in region ${region}..."

    try {
      OpenstackClientProvider clientProvider = description.credentials.provider

      LbPool lbPool = clientProvider.getLoadBalancerPool(region, loadBalancerId)

      if (lbPool) {
        lbPool.healthMonitors?.each { monitorId ->
          task.updateStatus BASE_PHASE, "Deleting health monitor ${monitorId} ..."
          clientProvider.disassociateAndRemoveHealthMonitor(region, loadBalancerId, monitorId)
          task.updateStatus BASE_PHASE, "Deleted health monitor ${monitorId}."
        }

        if (lbPool.vipId) {
          //NOTE: Deleting a vip will disassociate it from assigned floating IP
          task.updateStatus BASE_PHASE, "Deleting vip ${lbPool.vipId} ..."
          clientProvider.deleteVip(region, lbPool.vipId)
          task.updateStatus BASE_PHASE, "Deleted vip ${lbPool.vipId}."
        }

        //NOTE: Deleting a pool remove members
        task.updateStatus BASE_PHASE, "Deleting load balancing pool ${loadBalancerId} ..."
        clientProvider.deleteLoadBalancerPool(description.region, loadBalancerId)
        task.updateStatus BASE_PHASE, "Deleted load balancing pool ${loadBalancerId}."
      }
    } catch (OpenstackProviderException ope) {
      task.updateStatus BASE_PHASE, "Failed deleting load balancer ${ope.message}."
      throw new OpenstackOperationException(AtomicOperations.DELETE_LOAD_BALANCER, ope)
    }

    task.updateStatus BASE_PHASE, "Finished deleting load balancer ${loadBalancerId}."
  }
}
