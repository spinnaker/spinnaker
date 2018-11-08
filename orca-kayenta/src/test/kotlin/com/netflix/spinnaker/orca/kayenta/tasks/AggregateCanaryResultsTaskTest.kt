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
import java.time.Duration
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
            requisiteStageRefIds = if (i > 0) listOf("1<${i - 1}") else emptyList()
            refId = "1<$i"
            type = RunCanaryPipelineStage.STAGE_TYPE
            name = "${RunCanaryPipelineStage.STAGE_NAME_PREFIX}${i + 1}"
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
            "lifetimeDuration" to Duration.parse("PT1H")
          )
        }
      }
      // Reversing list of stages here which would cause test to fail if stages weren't first sorted by name before aggregating canary scores.
      val parentStage = pipeline.stageByRef("1")
      val reversedPipeline = pipeline {
        stages.addAll(pipeline.stages.reversed())
        stages.forEach { stage -> stage.execution = this }
        stages
          .filter { stage -> stage !== parentStage }
          .forEach { stage -> stage.parentStageId = parentStage.id }
      }
      val canaryStage = reversedPipeline.stageByRef("1")
      canaryStage.context["intervalStageId"] = canaryStage.id
      val taskResult = task.execute(canaryStage)

      it("stores the aggregated scores") {
        assertThat(taskResult.context["canaryScores"]).isEqualTo(canaryScores)
      }

      it("returns a status of $overallExecutionStatus") {
        assertThat(taskResult.status).isEqualTo(overallExecutionStatus)
      }
    }
  }

  describe("aggregation of canary scores from multiple canary stages is done independently") {
    where(
      "1st stage canary scores of %s and 2nd stage canary scores of %s and thresholds of %s",
      values(listOf(10.5, 40.0, 60.5), listOf(25.0, 35.0, 45.0), SUCCEEDED, TERMINAL),
      values(listOf(65.0), listOf(73.0), SUCCEEDED, SUCCEEDED),
      values(listOf(55.0), listOf(75.0), TERMINAL, SUCCEEDED),
      values(listOf(10.5, 40.0, 59.4), listOf(30.5, 40.5, 50.5), TERMINAL, TERMINAL)
    ) { canaryScores1, canaryScores2, overallExecutionStatus1, overallExecutionStatus2 ->

      val scoreThresholds = mapOf("pass" to 60.5)
      val pipeline = pipeline {
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
            "lifetimeDuration" to Duration.parse("PT1H")
          )
        }
        canaryScores1.forEachIndexed { i, score ->
          stage {
            requisiteStageRefIds = if (i > 0) listOf("1<${i - 1}") else emptyList()
            refId = "1<$i"
            parentStageId = stageByRef("1").id
            type = RunCanaryPipelineStage.STAGE_TYPE
            // Starting with 'Run Canary #9' to prove that the sorting is done treating everything after the '#' as a number.
            name = "${RunCanaryPipelineStage.STAGE_NAME_PREFIX}${i + 9}"
            context["canaryScore"] = score
          }
        }

        stage {
          requisiteStageRefIds = listOf("1")
          refId = "2"
          type = KayentaCanaryStage.STAGE_TYPE
          name = "kayentaCanary"
          context["canaryConfig"] = mapOf(
            "canaryConfigId" to UUID.randomUUID().toString(),
            "scopes" to listOf(mapOf(
              "controlScope" to "myapp-v010",
              "experimentScope" to "myapp-v021"
            )),
            "scoreThresholds" to scoreThresholds,
            "lifetimeDuration" to Duration.parse("PT1H")
          )
        }
        canaryScores2.forEachIndexed { i, score ->
          stage {
            requisiteStageRefIds = if (i > 0) listOf("1<${i - 1}") else emptyList()
            refId = "2<$i"
            parentStageId = stageByRef("2").id
            type = RunCanaryPipelineStage.STAGE_TYPE
            name = "${RunCanaryPipelineStage.STAGE_NAME_PREFIX}${i + 9}"
            context["canaryScore"] = score
          }
        }
      }
      // Reversing list of stages here which would cause test to fail if stages weren't first sorted by refIds / requisiteStageRefIds before aggregating canary scores.
      val reversedPipeline = pipeline {
        stages.addAll(pipeline.stages.reversed())
        stages.forEach { stage -> stage.execution = this }
      }

      val canaryStage1 = reversedPipeline.stageByRef("1")
      canaryStage1.context["intervalStageId"] = canaryStage1.id
      val taskResult1 = task.execute(canaryStage1)

      it("stores the aggregated scores independently") {
        assertThat(taskResult1.context["canaryScores"]).isEqualTo(canaryScores1)
      }

      it("returns a status of $overallExecutionStatus1 for the correct set of scores") {
        assertThat(taskResult1.status).isEqualTo(overallExecutionStatus1)
      }

      val canaryStage2 = reversedPipeline.stageByRef("2")
      canaryStage2.context["intervalStageId"] = canaryStage2.id
      val taskResult2 = task.execute(canaryStage2)

      it("stores the aggregated scores independently") {
        assertThat(taskResult2.context["canaryScores"]).isEqualTo(canaryScores2)
      }

      it("returns a status of $overallExecutionStatus2 for the correct set of scores") {
        assertThat(taskResult2.status).isEqualTo(overallExecutionStatus2)
      }
    }
  }
})
