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
import org.openstack4j.model.network.ext.LoadBalancerV2StatusTree

//TODO this needs to be tested and functionally verified
/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "disableServerGroup": { "serverGroupName": "myapp-teststack-v006", "region": "RegionOne", "account": "test" }} ]' localhost:7002/openstack/ops
 */
class DisableOpenstackAtomicOperation extends AbstractEnableDisableOpenstackAtomicOperation {
  final String phaseName = "DISABLE_SERVER_GROUP"

  DisableOpenstackAtomicOperation(OpenstackServerGroupAtomicOperationDescription description) {
    super(description)
  }

  @Override
  boolean isDisable() {
    true
  }

  @Override
  Void addOrRemoveInstancesFromLoadBalancer(List<String> instanceIds, List<String> loadBalancers) {
    task.updateStatus phaseName, "Deregistering instances from load balancers..."
    for (String id : instanceIds) {
      String ip = provider.getIpForInstance(description.region, id)
      loadBalancers.each { lbId ->
        LoadBalancerV2StatusTree status = provider.getLoadBalancerStatusTree(description.region, lbId)
        status.loadBalancerV2Status?.listenerStatuses?.each { listenerStatus ->
          listenerStatus.lbPoolV2Statuses?.each { poolStatus ->
            poolStatus.memberStatuses?.each { memberStatus ->
              if (ip && memberStatus.address && ip == memberStatus.address) {
                provider.removeMemberFromLoadBalancerPool(description.region, poolStatus.id, memberStatus.id)
              }
            }
          }
        }
      }
    }
  }

}
