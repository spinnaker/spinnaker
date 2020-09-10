package com.netflix.spinnaker.orca.remote.tasks

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import java.util.concurrent.TimeUnit

class MonitorRemoteStageTask : RetryableTask {
  override fun getTimeout(): Long = TimeUnit.HOURS.toMillis(1)
  override fun getBackoffPeriod(): Long = TimeUnit.SECONDS.toMillis(2)

  // TODO(jonsie): This is *VERY* incomplete - just a monitor task to monitor a context update for
  // now (remote stage updates context via a callback).  Specific context details will need to be
  // flushed out with some collaboration from Deck/UI folks.
  override fun execute(stage: StageExecution): TaskResult {
    var remoteStatus: ExecutionStatus = ExecutionStatus.RUNNING
    var outputs: MutableMap<String, Any> = mutableMapOf()

    if (stage.context.containsKey(REMOTE_STATUS)) {
      remoteStatus = ExecutionStatus.valueOf((stage.context[REMOTE_STATUS] as String).toUpperCase())
      if (stage.context.containsKey(REMOTE_RESULT)) {
        outputs = stage.context[REMOTE_RESULT] as MutableMap<String, Any>
      }
    }

    return if (remoteStatus == ExecutionStatus.SUCCEEDED || remoteStatus == ExecutionStatus.TERMINAL) {
      TaskResult.builder(remoteStatus).context(outputs).build()
    } else {
      TaskResult.builder(ExecutionStatus.RUNNING).context(outputs).build()
    }
  }

  companion object {
    const val REMOTE_PREFIX = "remote"
    const val REMOTE_STATUS = "$REMOTE_PREFIX.status"
    const val REMOTE_RESULT = "$REMOTE_PREFIX.result"
  }
}
