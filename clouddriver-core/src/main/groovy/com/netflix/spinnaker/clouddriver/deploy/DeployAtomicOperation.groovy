/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.deploy

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import org.springframework.beans.factory.annotation.Autowired

class DeployAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String TASK_PHASE = "DEPLOY"

  @Autowired
  DeployHandlerRegistry deploymentHandlerRegistry

  private final DeployDescription description

  DeployAtomicOperation(DeployDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Collection<OperationEvent> getEvents() {
    return this.description.getEvents() ?: []
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus TASK_PHASE, "Initializing phase."
    task.updateStatus TASK_PHASE, "Looking for ${description.getClass().simpleName} handler..."
    DeployHandler deployHandler = deploymentHandlerRegistry.findHandler(description)
    if (!deployHandler) {
      throw new DeployHandlerNotFoundException("Could not find handler for ${description.getClass().simpleName}!")
    }

    task.updateStatus TASK_PHASE, "Found handler: ${deployHandler.getClass().simpleName}"

    task.updateStatus TASK_PHASE, "Invoking Handler."

    DeploymentResult deploymentResult = deployHandler.handle(description, priorOutputs).normalize()
    task.updateStatus TASK_PHASE, "Server Groups: ${deploymentResult.getDeployments()} created."

    return deploymentResult
  }
}
