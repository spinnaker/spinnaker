package com.netflix.kato.deploy

import com.netflix.kato.data.task.InMemoryTaskRepository
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.orchestration.AtomicOperation
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired

@Log4j
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
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus TASK_PHASE, "Initializing phase."
    task.updateStatus TASK_PHASE, "Looking for ${description.getClass().simpleName} handler..."
    DeployHandler deployHandler = deploymentHandlerRegistry.findHandler(description)
    if (!deployHandler) {
      throw new DeployHandlerNotFoundException("Could not find handler for ${description.getClass().simpleName}!")
    }
    task.updateStatus TASK_PHASE, "Found handler: ${deployHandler.getClass().simpleName}"

    task.updateStatus TASK_PHASE, "Invoking Handler."
    def deploymentResult = deployHandler.handle(description)

    task.updateStatus TASK_PHASE, "Server Groups: ${deploymentResult.serverGroupNames} created."

    deploymentResult
  }
}
