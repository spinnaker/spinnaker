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

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ResizeOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.heat.Stack

class ResizeOpenstackAtomicOperation implements AtomicOperation<Void> {

  private final String BASE_PHASE = "RESIZE"

  //this is the name of the subtemplate referenced by the template,
  //and needs to be loaded into memory as a String
  final String SUBTEMPLATE_FILE = 'asg_resource.yaml'

  final String SUBTEMPLATE_OUTPUT = 'asg_resource'

  ResizeOpenstackAtomicOperationDescription description

  ResizeOpenstackAtomicOperation(ResizeOpenstackAtomicOperationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "myapp-teststack-v000", "capacity": { "min": 1, "max": 2 }, "account": "test", "region": "REGION1" }} ]' localhost:7002/openstack/ops
   * curl -X GET -H "Accept: application/json" localhost:7002/task/1
   */
  @Override
  Void operate(List priorOutputs) {
    try {
      task.updateStatus BASE_PHASE, "Initializing resizing of server group"
      OpenstackClientProvider provider = description.credentials.provider

      //get stack from server group
      task.updateStatus BASE_PHASE, "Fetching server group $description.serverGroupName"
      Stack stack = provider.getStack(description.region, description.serverGroupName)
      //we need to store subtemplate in asg output from create, as it is required to do an update and there is no native way of
      //obtaining it from a stack
      String subtemplate = stack.getOutputs().find { m -> m.get("output_key").equals(SUBTEMPLATE_OUTPUT) }.get("output_value")
      task.updateStatus BASE_PHASE, "Successfully fetched server group $description.serverGroupName"

      //update the min and max parameters
      ServerGroupParameters params = ServerGroupParameters.fromParamsMap(stack.parameters)
      ServerGroupParameters newParams = params.clone()
      newParams.identity {
        minSize = description.capacity.min
        maxSize = description.capacity.max
      }

      //get the current template from the stack
      task.updateStatus BASE_PHASE, "Fetching current template for server group $description.serverGroupName"
      String template = provider.getHeatTemplate(description.region, stack.name, stack.id)
      task.updateStatus BASE_PHASE, "Successfully fetched current template for server group $description.serverGroupName"

      //update stack
      task.updateStatus BASE_PHASE, "Updating server group $stack.name with new min size $newParams.minSize and max size $newParams.maxSize"
      provider.updateStack(description.region, stack.name, stack.id, template, [(SUBTEMPLATE_FILE): subtemplate], newParams)
      task.updateStatus BASE_PHASE, "Successfully updated server group $stack.name"

      task.updateStatus BASE_PHASE, "Successfully resized server group."
    } catch (Exception e) {
      throw new OpenstackOperationException(AtomicOperations.RESIZE_SERVER_GROUP, e)
    }
  }

}
