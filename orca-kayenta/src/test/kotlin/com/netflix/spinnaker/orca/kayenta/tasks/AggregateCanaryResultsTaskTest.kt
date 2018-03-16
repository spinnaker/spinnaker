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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.kayenta.pipeline.KayentaCanaryStage
import com.netflix.spinnaker.orca.kayenta.pipeline.RunCanaryPipelineStage
import com.netflix.spinnaker.spek.values
import com.netflix.spinnaker.spek.where
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.util.*

object AggregateCanaryResultsTaskSpec : Spek({

  val task = AggregateCanaryResultsTask()

  describe("aggregating canary scores") {
    where(
      "canary scores of %s and thresholds of %s",
      values(listOf(10.5, 40.0, 60.5), emptyMap(), SUCCEEDED),
      values(listOf(10.5, 40.0, 60.5), mapOf("pass" to 60.5), SUCCEEDED),
      values(listOf(10.5, 40.0, 60.5), mapOf("pass" to 55), SUCCEEDED),
      values(listOf(10.5, 40.0, 60.5), mapOf("marginal" to 5, "pass" to 60.5), SUCCEEDED),
      values(listOf(10.5, 40.0, 60.5), mapOf("marginal" to 5, "pass" to 55), SUCCEEDED),
      values(listOf(10.5, 40.0, 60.5), mapOf("marginal" to 5), SUCCEEDED),
      values(listOf(10.5, 40.0, 60.5), mapOf("marginal" to 5), SUCCEEDED),
      values(listOf(65.0), emptyMap(), SUCCEEDED),
      values(listOf(65.0), mapOf("pass" to 60.5), SUCCEEDED),
      values(listOf(65.0), mapOf("pass" to 55), SUCCEEDED),
      values(listOf(65.0), mapOf("marginal" to 5, "pass" to 60.5), SUCCEEDED),
      values(listOf(65.0), mapOf("marginal" to 5, "pass" to 55), SUCCEEDED),
      values(listOf(65.0), mapOf("marginal" to 5), SUCCEEDED),
      values(listOf(65.0), mapOf("marginal" to 5), SUCCEEDED),
      values(listOf(10.5, 40.0, 60.5), mapOf("pass" to 70), TERMINAL),
      values(listOf(10.5, 40.0, 60.5), mapOf("pass" to 70), TERMINAL),
      values(listOf(10.5, 40.0, 60.5), mapOf("marginal" to 5, "pass" to 70), TERMINAL),
      values(listOf(10.5, 40.0, 60.5), mapOf("marginal" to 5, "pass" to 70), TERMINAL),
      values(listOf(65.0), emptyMap(), SUCCEEDED),
      values(listOf(65.0), mapOf("pass" to 70), TERMINAL),
      values(listOf(65.0), mapOf("pass" to 70), TERMINAL),
      values(listOf(65.0), mapOf("marginal" to 5, "pass" to 70), TERMINAL),
      values(listOf(65.0), mapOf("marginal" to 5, "pass" to 70), TERMINAL),
      values(listOf(65.0), mapOf("marginal" to 68, "pass" to 70), TERMINAL),
      values(listOf(65.0), mapOf("marginal" to 68, "pass" to 70), TERMINAL),
      values(listOf(65.0), mapOf("marginal" to 68), TERMINAL),
      values(listOf(65.0), mapOf("marginal" to 68), TERMINAL)
    ) { canaryScores, scoreThresholds, overallExecutionStatus ->

      val pipeline = pipeline {
        canaryScores.forEachIndexed { i, score ->
          stage {
            refId = "1<$i"
            type = RunCanaryPipelineStage.STAGE_TYPE
            name = "runCanary"
            context["canaryScore"] = score
          }
        }
        stage {
          refId = "1"
          type = KayentaCanaryStage.STAGE_TYPE
          name = "kayentaCanary"
          context["canaryConfig"] = mapOf(
            "canaryConfigId" to UUID.randomUUID().toString(),
            "scopes" to listOf(mapOf(
              "controlScope" to "myapp-v010",
              "experimentScope" to "myapp-v021"
            )),
            "scoreThresholds" to scoreThresholds,
            "lifetimeHours" to "1"
          )
        }
      }
      val canaryStage = pipeline.stageByRef("1")

      val taskResult = task.execute(canaryStage)

      it("stores the aggregated scores") {
        assertThat(taskResult.context["canaryScores"]).isEqualTo(canaryScores)
      }

      it("returns a status of $overallExecutionStatus") {
        assertThat(taskResult.status).isEqualTo(overallExecutionStatus)
      }
    }
  }
})
