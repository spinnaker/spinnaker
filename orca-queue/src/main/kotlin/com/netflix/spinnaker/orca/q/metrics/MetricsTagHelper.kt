package com.netflix.spinnaker.orca.q.metrics

import com.netflix.spectator.api.BasicTag
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Stage

class MetricsTagHelper {
  companion object {
    fun commonTags(stage: Stage, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, status: ExecutionStatus): Iterable<BasicTag> =
      arrayListOf(
        BasicTag("status", status.toString()),
        BasicTag("executionType", stage.execution.type.name.capitalize()),
        BasicTag("isComplete", status.isComplete.toString()),
        BasicTag("cloudProvider", stage.context["cloudProvider"].toString()?: "n_a"))

    fun detailedTaskTags(stage: Stage, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, status: ExecutionStatus): Iterable<BasicTag> =
      arrayListOf(
        BasicTag("taskType", taskModel.implementingClass),
        BasicTag("account", stage.context["account"].toString()?: "n_a"),
        BasicTag("region", stage.context["region"].toString()?: "n_a"))
  }
}
