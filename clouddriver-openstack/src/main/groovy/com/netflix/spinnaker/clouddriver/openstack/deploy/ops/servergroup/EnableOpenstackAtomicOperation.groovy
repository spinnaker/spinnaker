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
import org.openstack4j.model.network.ext.LbPool

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "enableServerGroup": { "serverGroupName": "myapp-test-v000", "region": "RegionOne", "account": "test" }} ]' localhost:7002/openstack/ops
 */
class EnableOpenstackAtomicOperation extends AbstractEnableDisableOpenstackAtomicOperation {
  final String phaseName = "ENABLE_SERVER_GROUP"

  EnableOpenstackAtomicOperation(OpenstackServerGroupAtomicOperationDescription description) {
    super(description)
  }

  @Override
  boolean isDisable() {
    false
  }

  @Override
  Void addOrRemoveInstancesFromLoadBalancer(List<String> instanceIds, List<? extends LbPool> poolsForRegion) {
    task.updateStatus phaseName, "Registering instances with load balancers..."

    for (String id : instanceIds) {
      String ip = description.credentials.provider.getIpForInstance(description.region, id)
      for (LbPool pool : poolsForRegion) {
        Integer port = description.credentials.provider.getInternalLoadBalancerPort(pool)
        description.credentials.provider.addMemberToLoadBalancerPool(description.region, ip, pool.id, port, DEFAULT_WEIGHT)
      }
    }
  }
}
