package com.netflix.spinnaker.orca.q.metrics

import com.netflix.spectator.api.BasicTag
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.pipeline.model.Stage

class MetricsTagHelper : CloudProviderAware {
  companion object {
    private val helper = MetricsTagHelper()

    fun commonTags(stage: Stage, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, status: ExecutionStatus): Iterable<BasicTag> =
      arrayListOf(
        BasicTag("status", status.toString()),
        BasicTag("executionType", stage.execution.type.name.capitalize()),
        BasicTag("isComplete", status.isComplete.toString()),
        BasicTag("cloudProvider", helper.getCloudProvider(stage).valueOrNa())
      )

    fun detailedTaskTags(stage: Stage, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, status: ExecutionStatus): Iterable<BasicTag> =
      arrayListOf(
        BasicTag("taskType", taskModel.implementingClass),
        BasicTag("account", helper.getCredentials(stage).valueOrNa()),

        // sorting regions to reduce the metrics cardinality
        BasicTag("region", helper.getRegions(stage).let {
          if (it.isEmpty()) { "n_a" }
          else { it.sorted().joinToString(",") }
        }))

    private fun String?.valueOrNa(): String {
      return if (this == null || isBlank()) {
        "n_a"
      } else {
        this
      }
    }
  }
}
