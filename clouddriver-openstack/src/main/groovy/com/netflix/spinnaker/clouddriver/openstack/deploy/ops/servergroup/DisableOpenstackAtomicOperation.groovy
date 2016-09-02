/*
* Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.OpenstackServerGroupAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.network.ext.LoadBalancerV2StatusTree

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.function.Supplier

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "disableServerGroup": { "serverGroupName": "myapp-teststack-v006", "region": "RegionOne", "account": "test" }} ]' localhost:7002/openstack/ops
 */
class DisableOpenstackAtomicOperation extends AbstractEnableDisableOpenstackAtomicOperation {
  final String phaseName = "DISABLE_SERVER_GROUP"
  final String operation = AtomicOperations.DISABLE_SERVER_GROUP
  DisableOpenstackAtomicOperation(OpenstackServerGroupAtomicOperationDescription description) {
    super(description)
  }

  @Override
  boolean isDisable() {
    true
  }

  @Override
  Void addOrRemoveInstancesFromLoadBalancer(List<String> instanceIds, List<String> loadBalancerIds) {
    task.updateStatus phaseName, "Deregistering instances from load balancers..."
    Map<String, Future<LoadBalancerV2StatusTree>> statusTrees = [:]
    Map<String, Future<String>> ips = instanceIds.collectEntries { instanceId ->
      task.updateStatus phaseName, "Getting ip for instance $instanceId..."
      [(instanceId): CompletableFuture.supplyAsync({
        provider.getIpForInstance(description.region, instanceId)
      } as Supplier<String>).exceptionally { t ->
        null
      }]
    }
    loadBalancerIds.each { lbId ->
      task.updateStatus phaseName, "Getting load balancer tree for $lbId..."
      statusTrees << [(lbId): CompletableFuture.supplyAsync({
        provider.getLoadBalancerStatusTree(description.region, lbId)
      } as Supplier<LoadBalancerV2StatusTree>)]
    }
    CompletableFuture.allOf([statusTrees.values(), ips.values()].flatten() as CompletableFuture[]).join()
    for (String id : instanceIds) {
      String ip = ips[(id)].get()
      if (!ip) {
        task.updateStatus phaseName, "Could not find floating ip for instance $id, continuing with next instance"
      } else {
        loadBalancerIds.each { lbId ->
          LoadBalancerV2StatusTree status = statusTrees[(lbId)].get()
          status.loadBalancerV2Status?.listenerStatuses?.each { listenerStatus ->
            listenerStatus.lbPoolV2Statuses?.each { poolStatus ->
              poolStatus.memberStatuses?.each { memberStatus ->
                if (ip && memberStatus.address && ip == memberStatus.address) {
                  task.updateStatus phaseName, "Removing member instance $id with ip $ip from load balancer $lbId with listener ${listenerStatus.id} and pool ${poolStatus.id}..."
                  provider.removeMemberFromLoadBalancerPool(description.region, poolStatus.id, memberStatus.id)
                }
              }
            }
          }
        }
      }
    }
    task.updateStatus phaseName, "Finished deregistering instances from load balancers."
  }

}
