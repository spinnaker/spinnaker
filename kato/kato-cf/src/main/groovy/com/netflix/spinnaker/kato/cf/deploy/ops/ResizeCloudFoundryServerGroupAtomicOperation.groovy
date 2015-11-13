package com.netflix.spinnaker.kato.cf.deploy.ops

import com.netflix.spinnaker.kato.cf.deploy.description.ResizeCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryClientFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class ResizeCloudFoundryServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP"

  @Autowired
  CloudFoundryClientFactory cloudFoundryClientFactory

  private final ResizeCloudFoundryServerGroupDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  ResizeCloudFoundryServerGroupAtomicOperation(ResizeCloudFoundryServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing resize of server group $description.serverGroupName in " +
        "$description.zone..."

    def client = cloudFoundryClientFactory.createCloudFoundryClient(description.credentials, true)

    client.updateApplicationInstances(description.serverGroupName, description.targetSize)

    task.updateStatus BASE_PHASE, "Done resizing server group $description.serverGroupName in $description.zone."
    null
  }
}
