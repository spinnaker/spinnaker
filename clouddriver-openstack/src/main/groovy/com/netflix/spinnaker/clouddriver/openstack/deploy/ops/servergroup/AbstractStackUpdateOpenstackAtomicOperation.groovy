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
import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.OpenstackServerGroupAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
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
   * Get the server group name to operate on. Defaults to what was passed in.
   * If server group name was not passed in, you can override to find an alternate server group to work with.
   * @return
   */
  String getServerGroupName() {
    description.serverGroupName
  }

  /**
   * Return the new parameters that you want to apply to the stack.
   * Defaults to return existing parameters.
   * @param stack
   * @return
   */
  ServerGroupParameters buildServerGroupParameters(Stack stack) {
    ServerGroupParameters.fromParamsMap(stack.parameters)
  }

  /**
   * Defaults to noop.
   * @param stack
   */
  void preUpdate(Stack stack) {
  }

  /**
   * Defaults to noop.
   * @param stack
   */
  void postUpdate(Stack stack) {
  }

  @Override
  Void operate(List priorOutputs) {
    try {
      task.updateStatus phaseName, "Initializing $operation"
      OpenstackClientProvider provider = description.credentials.provider

      //get stack from server group
      String foundServerGroupName = serverGroupName
      task.updateStatus phaseName, "Fetching server group $foundServerGroupName"
      Stack stack = provider.getStack(description.region, foundServerGroupName)
      if (!stack) {
        throw new OpenstackResourceNotFoundException("Could not find stack $foundServerGroupName in region: $description.region")
      }

      //pre update ops
      preUpdate(stack)

      String resourceFileName = ServerGroupConstants.SUBTEMPLATE_FILE

      List<Map<String, Object>> outputs = stack.outputs
      String resourceSubtemplate = outputs.find { m -> m.get("output_key") == ServerGroupConstants.SUBTEMPLATE_OUTPUT }.get("output_value")
      String memberTemplate = outputs.find { m -> m.get("output_key") == ServerGroupConstants.MEMBERTEMPLATE_OUTPUT }.get("output_value")
      task.updateStatus phaseName, "Successfully fetched server group $foundServerGroupName"

      //get the current template from the stack
      task.updateStatus phaseName, "Fetching current template for server group $foundServerGroupName"
      String template = provider.getHeatTemplate(description.region, stack.name, stack.id)
      task.updateStatus phaseName, "Successfully fetched current template for server group $foundServerGroupName"

      Map<String, String> templateMap = [(resourceFileName): resourceSubtemplate]
      if (memberTemplate) {
        templateMap << [(ServerGroupConstants.MEMBERTEMPLATE_FILE): memberTemplate]
      }

      //update stack
      task.updateStatus phaseName, "Updating server group $stack.name"
      provider.updateStack(description.region, stack.name, stack.id, template, templateMap, buildServerGroupParameters(stack), stack.tags)

      task.updateStatus phaseName, "Waiting on heat stack update status ${stack.name}..."
      def config = description.credentials.credentials.stackConfig
      StackChecker stackChecker = new StackChecker(StackChecker.Operation.UPDATE)
      BlockingStatusChecker statusChecker = BlockingStatusChecker.from(config.pollTimeout, config.pollInterval, stackChecker)
      statusChecker.execute {
        provider.getStack(description.region, description.serverGroupName)
      }
      task.updateStatus phaseName, "Successfully updated server group $stack.name"

      //post update ops
      postUpdate(stack)

      task.updateStatus phaseName, "Successfully completed $operation."
    } catch (Exception e) {
      throw new OpenstackOperationException(operation, e)
    }
  }
}
