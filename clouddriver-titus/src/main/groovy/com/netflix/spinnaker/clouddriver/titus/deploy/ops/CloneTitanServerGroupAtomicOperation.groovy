/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.ops
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.titus.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitanDeployDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitanDeployHandler

/**
 *
 *
 */
class CloneTitanServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {

  private static final String PHASE = "CLONE_TITAN_SERVER_GROUP"

  private final TitanClientProvider titanClientProvider
  private final TitanDeployDescription description
  private final TitanDeployHandler titanDeployHandler

  CloneTitanServerGroupAtomicOperation(TitanClientProvider titanClientProvider,
                                       TitanDeployDescription description,
                                       TitanDeployHandler titanDeployHandler) {
    this.titanClientProvider = titanClientProvider
    this.description = description
    this.titanDeployHandler = titanDeployHandler
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus(PHASE,
      "Cloning server group in ${description.account}:${description.region}${description.subnet ? ':' + description.subnet : ''}...")
    titanDeployHandler.handle(description, priorOutputs)
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
