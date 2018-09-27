package com.netflix.spinnaker.keel.orchestration

import com.netflix.spinnaker.keel.api.AssetId
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRef
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(TaskMonitor::class)
class InMemoryTaskMonitor(
  private val orcaService: OrcaService
) : TaskMonitor {
  private val tasks = mutableMapOf<AssetId, TaskRef>()

  override fun monitorTask(assetId: AssetId, taskRef: TaskRef) {
    tasks[assetId] = taskRef
  }

  override fun isInProgress(assetId: AssetId): Boolean {
    val taskRef = tasks[assetId]
    return when (taskRef) {
      null -> false
      else -> orcaService.getTask(taskRef.id).status.isIncomplete()
    }.also {
      if (!it) tasks.remove(assetId)
    }
  }
}
