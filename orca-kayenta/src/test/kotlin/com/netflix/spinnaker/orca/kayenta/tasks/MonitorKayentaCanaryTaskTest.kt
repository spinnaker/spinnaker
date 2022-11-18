/*
 * Copyright 2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.kayenta.CanaryResult
import com.netflix.spinnaker.orca.kayenta.CanaryResults
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.JudgeResult
import com.netflix.spinnaker.orca.kayenta.JudgeScore
import com.netflix.spinnaker.orca.kayenta.Thresholds
import com.netflix.spinnaker.orca.kayenta.pipeline.RunCanaryPipelineStage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.Duration
import java.time.Instant


object MonitorKayentaCanaryTaskSpec : Spek({
  val canaryResults: CanaryResults = CanaryResults(
    true,
    ExecutionStatus.SUCCEEDED.toString(),
    CanaryResult(JudgeResult(JudgeScore(33, "", ""), emptyArray()), Duration.ofHours(1)),
    Instant.ofEpochMilli(1521059331684),
    Instant.ofEpochMilli(1521059331909),
    Instant.ofEpochMilli(1521059341101),
    "account name",
    "app name",
    null,
    null
  );
  val kayentaService: KayentaService = Mockito.mock(KayentaService::class.java)
  `when`(kayentaService.getCanaryResults(Mockito.anyString(), Mockito.anyString())).thenReturn(canaryResults)
  val subject: MonitorKayentaCanaryTask = MonitorKayentaCanaryTask(kayentaService)

  describe("aggregating canary scores") {
    val objectMapper = ObjectMapper()

    val pipeline = pipeline {
      stage {
        refId = "1"
        type = RunCanaryPipelineStage.STAGE_TYPE
        name = "${RunCanaryPipelineStage.STAGE_NAME_PREFIX}"
        context["canaryPipelineExecutionId"] = "1"
        context["storageAccountName"] = "account name"
        context["metricsAccountName"] = "metrics name"
      }
    }

    val stage = pipeline.stageByRef("1")
    given("a marginal major than canary results") {
      action("the task is executed and validated correct data map") {
        stage.context["scoreThresholds"] = Thresholds(pass = 50, marginal = 75)
        assertDoesNotThrow { objectMapper.writeValueAsString(subject.execute(stage).context) }
      }
    }

    given("a marginal minor than canary results") {
      action("the task is executed and validated correct data map") {
        stage.context["scoreThresholds"] = Thresholds(pass = 50, marginal = 30)
        assertDoesNotThrow { objectMapper.writeValueAsString(subject.execute(stage).context) }
      }
    }
  }
})
