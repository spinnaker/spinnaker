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

import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.LbaasConfig
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.DeleteOpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.StackPoolMemberAware
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import groovy.util.logging.Slf4j
import org.openstack4j.model.network.ext.LbProvisioningStatus
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2

/**
 * Removes an openstack load balancer.
 */
@Slf4j
class DeleteOpenstackLoadBalancerAtomicOperation extends AbstractOpenstackLoadBalancerAtomicOperation implements AtomicOperation<Void>, StackPoolMemberAware {

  static final String BASE_PHASE = 'DELETE_LOAD_BALANCER'
  DeleteOpenstackLoadBalancerDescription description

  DeleteOpenstackLoadBalancerAtomicOperation(DeleteOpenstackLoadBalancerDescription description) {
    super(description.credentials)
    this.description = description
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "deleteLoadBalancer": { "id": "6adc02a8-7b01-4f90-9e6f-9a4c3411e7ad", "region": "RegionOne", "account":  "test" } } ]' localhost:7002/openstack/ops
   */

  @Override
  Void operate(List priorOutputs) {
    String region = description.region
    String loadBalancerId = description.id
    OpenstackClientProvider provider = description.credentials.provider

    try {
      task.updateStatus BASE_PHASE, "Deleting load balancer ${loadBalancerId} in region ${region}..."

      task.updateStatus BASE_PHASE, "Fetching status tree..."
      LoadBalancerV2 loadBalancer = provider.getLoadBalancer(region, loadBalancerId)
      task.updateStatus BASE_PHASE, "Fetched status tree."

      if (loadBalancer) {
        checkPendingLoadBalancerState(loadBalancer)

        //step 1 - delete load balancer
        deleteLoadBalancer(region, loadBalancer)

        //step 2 - update stack(s) that reference load balancer
        updateServerGroup(BASE_PHASE, region, loadBalancerId, [loadBalancerId])
      }
    } catch (OpenstackProviderException e) {
      task.updateStatus BASE_PHASE, "Failed deleting load balancer ${e.message}."
      throw new OpenstackOperationException(AtomicOperations.DELETE_LOAD_BALANCER, e)
    }

    task.updateStatus BASE_PHASE, "Finished deleting load balancer ${loadBalancerId}."
  }

  /**
   * Delete the load balancer and all sub-elements.
   * @param loadBalancerStatus
   */
  void deleteLoadBalancer(String region, LoadBalancerV2 loadBalancer) {
    Map<String, ListenerV2> listenerMap = buildListenerMap(region, loadBalancer)

    this.deleteLoadBalancerPeripherals(BASE_PHASE, region, loadBalancer.id, listenerMap.values())

    //delete load balancer
    task.updateStatus BASE_PHASE, "Deleting load balancer $loadBalancer.id in $region ..."
    createBlockingDeletedStatusChecker(region, loadBalancer.id).execute {
      provider.deleteLoadBalancer(region, loadBalancer.id)
    }
    task.updateStatus BASE_PHASE, "Deleted load balancer $loadBalancer.id in $region."
  }

  /**
   * Creates and returns a new blocking deleted status checker.
   * @param region
   * @param loadBalancerId
   * @return
   */
  BlockingStatusChecker createBlockingDeletedStatusChecker(String region, String loadBalancerId) {
    LbaasConfig config = this.description.credentials.credentials.lbaasConfig
    BlockingStatusChecker.from(config.pollTimeout, config.pollInterval) {
      LoadBalancerV2 loadBalancer
      try {
        loadBalancer = provider.getLoadBalancer(region, loadBalancerId)
      } catch (OpenstackProviderException e) {
        // Get load balancer will throw an exception if the load balancer is not found
      }

      // Short circuit polling if openstack is unable to provision the load balancer
      if (loadBalancer && LbProvisioningStatus.ERROR == loadBalancer.provisioningStatus) {
        throw new OpenstackProviderException("Openstack was unable to provision load balancer ${loadBalancerId}")
      }

      loadBalancer == null
    }
  }
}
