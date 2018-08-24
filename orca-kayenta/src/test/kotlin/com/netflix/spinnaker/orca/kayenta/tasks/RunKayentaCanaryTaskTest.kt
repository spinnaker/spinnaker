/*
 * Copyright 2018 Google, Inc.
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

import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryServerGroupsStage
import com.netflix.spinnaker.orca.kayenta.pipeline.RunCanaryPipelineStage
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

import java.time.Duration
import java.time.Instant
import java.util.*

object RunKayentaCanaryTaskTest : Spek({

  val kayenta: KayentaService = mock()
  val subject = RunKayentaCanaryTask(kayenta)

  given("an upstream stage that generated a summary of upstream control & experiment server groups") {
    val pipeline = pipeline {
      stage {
        refId = "1"
        type = DeployCanaryServerGroupsStage.STAGE_TYPE
        name = "deployCanaryServerGroups"
        outputs["deployedServerGroups"] = listOf(
          mapOf(
            "controlScope" to "app-control-v000",
            "controlLocation" to "us-central1",
            "experimentScope" to "app-experiment-v000",
            "experimentLocation" to "us-central1"
          )
        )
      }
      stage {
        refId = "2"
        requisiteStageRefIds = listOf("1")
        type = RunCanaryPipelineStage.STAGE_TYPE
        context = mapOf(
          "canaryConfigId" to UUID.randomUUID().toString(),
          "parentPipelineExecutionId" to "ABC",
          "scopes" to mapOf(
            "default" to mapOf(
              "controlScope" to mapOf(
                "start" to Instant.now().epochSecond,
                "end" to Instant.now().epochSecond
              ),
              "experimentScope" to mapOf(
                "start" to Instant.now().epochSecond,
                "end" to Instant.now().epochSecond
              )
            )
          ),
          "scoreThresholds" to mapOf(
            "pass" to 90,
            "marginal" to 50
          ),
          "lifetimeDuration" to Duration.parse("PT1H")
        )
      }
    }

    beforeGroup {
      whenever(kayenta.create(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
        mapOf("canaryExecutionId" to "ABC")
      )
    }

    on("executing the task") {
      subject.execute(pipeline.stageByRef("2"))
    }

    it("executes Kayenta canary request with hydrated scopes") {
      verify(kayenta).create(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), check {
        it.scopes["default"].let {
          assertThat(it?.controlScope?.location).isEqualTo("us-central1")
          assertThat(it?.controlScope?.scope).isEqualTo("app-control-v000")
          assertThat(it?.experimentScope?.location).isEqualTo("us-central1")
          assertThat(it?.experimentScope?.scope).isEqualTo("app-experiment-v000")
        }
      })
    }
  }
})
