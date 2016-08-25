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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.OpenstackServerGroupAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.openstack4j.model.heat.Stack

abstract class AbstractStackUpdateOpenstackAtomicOperation implements AtomicOperation<Void> {

  OpenstackServerGroupAtomicOperationDescription description

  AbstractStackUpdateOpenstackAtomicOperation(OpenstackServerGroupAtomicOperationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /**
   * Return the phase name.
   * @return
   */
  abstract String getPhaseName()

  /**
   * Returm the operation.
   * @return
   */
  abstract String getOperation()

  /**
   * Return the new parameters that you want to apply to the stack.
   * @param stack
   * @return
   */
  abstract ServerGroupParameters buildServerGroupParameters(Stack stack)

  @Override
  Void operate(List priorOutputs) {
    try {
      task.updateStatus phaseName, "Initializing $operation"
      OpenstackClientProvider provider = description.credentials.provider

      //get stack from server group
      task.updateStatus phaseName, "Fetching server group $description.serverGroupName"
      Stack stack = provider.getStack(description.region, description.serverGroupName)
      //we need to store subtemplate in asg output from create, as it is required to do an update and there is no native way of
      //obtaining it from a stack
      List<Map<String, Object>> outputs = stack.outputs
      String subtemplate = outputs.find { m -> m.get("output_key") == ServerGroupConstants.SUBTEMPLATE_OUTPUT }.get("output_value")
      String memberTemplate = outputs.find { m -> m.get("output_key") == ServerGroupConstants.MEMBERTEMPLATE_OUTPUT }.get("output_value")
      task.updateStatus phaseName, "Successfully fetched server group $description.serverGroupName"

      //get the current template from the stack
      task.updateStatus phaseName, "Fetching current template for server group $description.serverGroupName"
      String template = provider.getHeatTemplate(description.region, stack.name, stack.id)
      task.updateStatus phaseName, "Successfully fetched current template for server group $description.serverGroupName"

      //update stack
      task.updateStatus phaseName, "Updating server group $stack.name"
      provider.updateStack(description.region, stack.name, stack.id, template, [(ServerGroupConstants.SUBTEMPLATE_FILE): subtemplate, (ServerGroupConstants.MEMBERTEMPLATE_FILE): memberTemplate], buildServerGroupParameters(stack), stack.tags)
      task.updateStatus phaseName, "Successfully updated server group $stack.name"

      task.updateStatus phaseName, "Successfully completed $operation."
    } catch (Exception e) {
      throw new OpenstackOperationException(operation, e)
    }
  }

}
