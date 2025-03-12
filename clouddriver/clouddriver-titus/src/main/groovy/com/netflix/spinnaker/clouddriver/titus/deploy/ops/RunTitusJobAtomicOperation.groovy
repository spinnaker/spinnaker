/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.titus.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.SagaContextAware
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusDeployHandler

import javax.annotation.Nonnull

class RunTitusJobAtomicOperation implements AtomicOperation<DeploymentResult>, SagaContextAware {

  private static final String PHASE = "RUN_TITUS_JOB"

  private final TitusClientProvider titusClientProvider
  private final TitusDeployDescription description
  private final TitusDeployHandler titusDeployHandler

  RunTitusJobAtomicOperation(TitusClientProvider titusClientProvider,
                             TitusDeployDescription description,
                             TitusDeployHandler titusDeployHandler) {
    this.titusClientProvider = titusClientProvider
    this.description = description
    this.titusDeployHandler = titusDeployHandler
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus(PHASE,
      "Running job in ${description.account}:${description.region}${description.subnet ? ':' + description.subnet : ''}...")
    description.jobType = 'batch'
    titusDeployHandler.handle(description, priorOutputs)
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  void setSagaContext(@Nonnull SagaContext sagaContext) {
    description.sagaContext = sagaContext
  }

  @Override
  SagaContext getSagaContext() {
    return description.sagaContext
  }
}
