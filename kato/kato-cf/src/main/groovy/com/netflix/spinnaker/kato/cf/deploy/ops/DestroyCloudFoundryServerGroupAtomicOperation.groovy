package com.netflix.spinnaker.kato.cf.deploy.ops

import com.netflix.spinnaker.kato.cf.deploy.description.DestroyCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryClientFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DestroyCloudFoundryServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  @Autowired
  CloudFoundryClientFactory cloudFoundryClientFactory

  private final DestroyCloudFoundryServerGroupDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DestroyCloudFoundryServerGroupAtomicOperation(DestroyCloudFoundryServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing destruction of server group $description.serverGroupName in $description.zone..."

    def client = cloudFoundryClientFactory.createCloudFoundryClient(description.credentials, true)

    try {
      client.deleteApplication(description.serverGroupName)

      task.updateStatus BASE_PHASE, "Done destroying server group $description.serverGroupName in $description.zone."
    } catch (Exception e) {
      task.updateStatus BASE_PHASE, "Failed to delete server group $description.serverGroupName => $e.message"
    }

    null
  }
}
