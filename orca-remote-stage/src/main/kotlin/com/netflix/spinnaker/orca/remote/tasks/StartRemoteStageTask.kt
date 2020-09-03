package com.netflix.spinnaker.orca.remote.tasks

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.remote.model.RemoteStageExtensionPayload
import com.netflix.spinnaker.orca.remote.service.RemoteStageExtensionService
import java.util.concurrent.TimeUnit

class StartRemoteStageTask(
  private val remoteStageExtensionService: RemoteStageExtensionService
) : RetryableTask {
  override fun getTimeout(): Long = TimeUnit.SECONDS.toMillis(60)
  override fun getBackoffPeriod(): Long = TimeUnit.SECONDS.toMillis(2)

  override fun execute(stage: StageExecution): TaskResult {
    val remoteExtension = remoteStageExtensionService.getByStageType(stage.type)
    val remoteStageExtensionPayload = RemoteStageExtensionPayload(
      type = stage.type,
      id = stage.id,
      pipelineExecutionId = stage.execution.id,
      context = stage.context
    )

    remoteExtension.invoke(remoteStageExtensionPayload)

    val outputs: MutableMap<String, Any> = mutableMapOf(
      "pluginId" to remoteExtension.pluginId,
      "extensionId" to remoteExtension.id
    )

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }
}
