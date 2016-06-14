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

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.CloneOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import groovy.util.logging.Slf4j
import org.openstack4j.openstack.heat.domain.HeatStack

@Slf4j
class CloneOpenstackAtomicOperation implements AtomicOperation<DeploymentResult>{
  private static final String BASE_PHASE = "CLONE_SERVER_GROUP"

  CloneOpenstackAtomicOperationDescription description

  CloneOpenstackAtomicOperation(CloneOpenstackAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
  * curl -X POST -H "Content-Type: application/json" -d '[{"cloneServerGroup": {"source": {"stackName": "myapp-teststack-v000", "region": "RegionOne"},"account": "test"}}]' localhost:7002/openstack/ops
  */
  @Override
  DeploymentResult operate (List priorOutputs) {
    CloneOpenstackAtomicOperationDescription newDescription = cloneAndOverrideDescription()

    task.updateStatus BASE_PHASE, "Initializing cloning of Heat stack ${description.source.stackName}..."

    DeployOpenstackAtomicOperation deployer = new DeployOpenstackAtomicOperation(newDescription)
    DeploymentResult deploymentResult = deployer.operate(priorOutputs) // right now this is null from deployOpenstackAtomicOperation

    task.updateStatus BASE_PHASE, "Finished copying job for ${description.source.stackName}."

    return deploymentResult
  }

  CloneOpenstackAtomicOperationDescription cloneAndOverrideDescription() {
    CloneOpenstackAtomicOperationDescription newDescription = description.clone()

    task.updateStatus BASE_PHASE, "Reading ancestor stack name ${description.source.stackName}..."

    HeatStack ancestorStack = description.credentials.provider.getServerGroup(description.source.region, description.source.stackName)
    if (!ancestorStack) {
      throw new OpenstackResourceNotFoundException(AtomicOperations.CLONE_SERVER_GROUP, "Source stack ${description.source.stackName} does not exist.")
    }

    def ancestorNames = Names.parseName(description.source.stackName)

    // Build description of object from ancestor, override any values that were specified on the clone call
    newDescription.application = description.application ?: ancestorNames.app
    newDescription.stack = description.stack ?: ancestorNames.stack
    newDescription.freeFormDetails = description.freeFormDetails ?: ancestorNames.detail
    newDescription.region = description.region ?: description.source.region
    newDescription.heatTemplate = description.heatTemplate ?: description.credentials.provider.getHeatTemplate(description.source.region, ancestorStack.name, ancestorStack.id)
    newDescription.parameters = description.parameters ?: [:]
    newDescription.disableRollback = description.disableRollback ?: false
    newDescription.timeoutMins = description.timeoutMins ?: ancestorStack.timeoutMins

    return newDescription
  }
}
