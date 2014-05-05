package com.netflix.kato.deploy.aws.ops

import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.description.DeleteAsgDescription
import com.netflix.kato.orchestration.AtomicOperation


import static com.netflix.kato.deploy.aws.StaticAmazonClients.getAutoScaling

class DeleteAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  final DeleteAsgDescription description

  DeleteAsgAtomicOperation(DeleteAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Cluster Shrinking Operation..."
    def autoScaling = getAutoScaling(description.credentials, description.region)

    task.updateStatus BASE_PHASE, "Removing ASG -> ${description.asgName}"
    def request = new DeleteAutoScalingGroupRequest().withAutoScalingGroupName(description.asgName)
        .withForceDelete(description.forceDelete)
    autoScaling.deleteAutoScalingGroup(request)
    task.updateStatus BASE_PHASE, "Deleted ASG -> ${description.asgName}"
    task.updateStatus BASE_PHASE, "Finished Shrinking Cluster."
  }
}
