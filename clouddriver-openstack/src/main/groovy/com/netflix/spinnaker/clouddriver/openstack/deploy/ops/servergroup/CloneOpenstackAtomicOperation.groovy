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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.heat.Stack

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
  * curl -X POST -H "Content-Type: application/json" -d '[{"cloneServerGroup": {"source": {"serverGroupName": "myapp-teststack-v000", "region": "RegionOne"}, "region": "RegionTwo", "account": "test"}}]' localhost:7002/openstack/ops
  * curl -X GET -H "Accept: application/json" localhost:7002/task/1
  */
  @Override
  DeploymentResult operate (List priorOutputs) {
    DeploymentResult deploymentResult
    try {
      DeployOpenstackAtomicOperationDescription newDescription = cloneAndOverrideDescription()

      task.updateStatus BASE_PHASE, "Initializing cloning of server group ${description.source.serverGroupName}"

      DeployOpenstackAtomicOperation deployer = new DeployOpenstackAtomicOperation(newDescription)
      deploymentResult = deployer.operate(priorOutputs)

      task.updateStatus BASE_PHASE, "Finished cloning server group ${description.source.serverGroupName}"
    } catch (Exception e) {
      throw new OpenstackOperationException(AtomicOperations.CLONE_SERVER_GROUP, e)
    }
    deploymentResult
  }

  DeployOpenstackAtomicOperationDescription cloneAndOverrideDescription() {
    DeployOpenstackAtomicOperationDescription deployDescription = description.clone()

    task.updateStatus BASE_PHASE, "Reading ancestor stack name ${description.source.serverGroupName}"

    Stack ancestorStack = description.credentials.provider.getStack(description.source.region, description.source.serverGroupName)
    if (!ancestorStack) {
      throw new OpenstackOperationException(AtomicOperations.CLONE_SERVER_GROUP, "Source stack ${description.source.serverGroupName} does not exist")
    }
    ServerGroupParameters ancestorParams = ServerGroupParameters.fromParamsMap(ancestorStack.parameters)
    Names ancestorNames = Names.parseName(description.source.serverGroupName)

    task.updateStatus BASE_PHASE, "Done reading ancestor stack name ${description.source.serverGroupName}"

    task.updateStatus BASE_PHASE, "Creating new server group description"

    deployDescription.application = description.application ?: ancestorNames.app
    deployDescription.stack = description.stack ?: ancestorNames.stack
    deployDescription.freeFormDetails = description.freeFormDetails ?: ancestorNames.detail
    deployDescription.serverGroupParameters = description.serverGroupParameters ?: new ServerGroupParameters()
    deployDescription.serverGroupParameters.with {
      image = description.serverGroupParameters?.image ?: ancestorParams.image
      instanceType = description.serverGroupParameters?.instanceType ?: ancestorParams.instanceType
      maxSize = description.serverGroupParameters?.maxSize ?: ancestorParams.maxSize
      minSize = description.serverGroupParameters?.minSize ?: ancestorParams.minSize
      desiredSize = description.serverGroupParameters?.desiredSize ?: ancestorParams.desiredSize
      subnetId = description.serverGroupParameters?.subnetId ?: ancestorParams.subnetId
      poolId = description.serverGroupParameters?.poolId ?: ancestorParams.poolId
      securityGroups = description.serverGroupParameters?.securityGroups ?: ancestorParams.securityGroups
    }
    deployDescription.disableRollback = description.disableRollback ?: false
    deployDescription.timeoutMins = description.timeoutMins ?: ancestorStack.timeoutMins
    deployDescription.region = description.region

    task.updateStatus BASE_PHASE, "Finished creating new server group description"

    deployDescription
  }
}
