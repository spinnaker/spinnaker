/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
