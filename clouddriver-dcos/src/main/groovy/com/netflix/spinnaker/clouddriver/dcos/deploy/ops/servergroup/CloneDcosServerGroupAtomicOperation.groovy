/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.CloneDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class CloneDcosServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "CLONE_SERVER_GROUP"

  final DcosClientProvider dcosClientProvider
  final CloneDcosServerGroupDescription description
  final DeployDcosServerGroupDescriptionToAppMapper descriptionToAppMapper

  CloneDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider, DeployDcosServerGroupDescriptionToAppMapper descriptionToAppMapper, CloneDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.descriptionToAppMapper = descriptionToAppMapper
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  DeploymentResult operate(List priorOutputs) {

    task.updateStatus BASE_PHASE, "Initializing copy of server group for " +
      "${description.source.serverGroupName}..."

    CloneDcosServerGroupDescription newDescription = cloneAndOverrideDescription()

    DeployDcosServerGroupAtomicOperation deployer = new DeployDcosServerGroupAtomicOperation(dcosClientProvider, descriptionToAppMapper, newDescription)
    DeploymentResult deploymentResult = deployer.operate(priorOutputs)

    task.updateStatus BASE_PHASE, "Finished copying server group for " +
      "${description.source.serverGroupName}."

    task.updateStatus BASE_PHASE, "Finished copying server group for " +
      "${description.source.serverGroupName}. " +
      "New server group = ${deploymentResult.serverGroupNames[0]}."

    return deploymentResult
  }

  CloneDcosServerGroupDescription cloneAndOverrideDescription() {
    CloneDcosServerGroupDescription newDescription = description.clone()

    // Unset fields that can't be set for a new server group.
    newDescription.version = null

    return newDescription
  }
}
