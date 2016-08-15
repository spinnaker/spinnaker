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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ResizeOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.heat.Stack

class ResizeOpenstackAtomicOperation extends AbstractStackUpdateOpenstackAtomicOperation {

  final String phaseName = "RESIZE"

  final String operation = AtomicOperations.RESIZE_SERVER_GROUP

  ResizeOpenstackAtomicOperation(ResizeOpenstackAtomicOperationDescription description) {
    super(description)
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "myapp-teststack-v000", "capacity": { "min": 1, "desired": 2, "max": 3 }, "account": "test", "region": "REGION1" }} ]' localhost:7002/openstack/ops
   * curl -X GET -H "Accept: application/json" localhost:7002/task/1
   */

  @Override
  ServerGroupParameters buildServerGroupParameters(Stack stack) {
    ServerGroupParameters params = ServerGroupParameters.fromParamsMap(stack.parameters)
    ServerGroupParameters newParams = params.clone()
    newParams.with {
      minSize = description.capacity.min
      maxSize = description.capacity.max
      desiredSize = description.capacity.desired
      it
    }
  }

}
