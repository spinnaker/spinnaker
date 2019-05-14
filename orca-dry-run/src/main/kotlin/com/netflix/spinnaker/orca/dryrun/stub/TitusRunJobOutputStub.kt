/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.dryrun.stub

import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class TitusRunJobOutputStub : OutputStub {

  override fun supports(stage: Stage) =
    stage.type == "runJob" && stage.context["cloudProvider"] == "titus"

  override fun outputs(stage: Stage): Map<String, Any> {
    val app = stage.execution.application
    val account = stage.context["credentials"]
    val cluster = stage.context["cluster"] as Map<String, Any>
    val region = cluster["region"]
    val jobId = UUID.randomUUID().toString()
    val taskId = UUID.randomUUID().toString()
    val instanceId = UUID.randomUUID().toString()
    return mapOf(
      "jobStatus" to mapOf(
        "id" to jobId,
        "name" to "$app-v000",
        "type" to "titus",
        "createdTime" to 0,
        "provider" to "titus",
        "account" to account,
        "application" to app,
        "region" to "$region",
        "completionDetails" to mapOf(
          "taskId" to taskId,
          "instanceId" to instanceId
        ),
        "jobState" to "Succeeded"
      ),
      "completionDetails" to mapOf(
        "taskId" to taskId,
        "instanceId" to instanceId
      )
    )
  }
}
