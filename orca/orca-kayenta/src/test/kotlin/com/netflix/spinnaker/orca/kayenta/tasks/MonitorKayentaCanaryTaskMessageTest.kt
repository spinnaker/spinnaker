/*
 * Copyright 2026 DoorDash, Inc.
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

import com.netflix.spinnaker.orca.kayenta.config.KayentaCanaryReportProperties
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.kayenta.CanaryResult
import com.netflix.spinnaker.orca.kayenta.CanaryResults
import com.netflix.spinnaker.orca.kayenta.JudgeResult
import com.netflix.spinnaker.orca.kayenta.JudgeScore
import com.netflix.spinnaker.orca.kayenta.KayentaCredential
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.Thresholds
import com.netflix.spinnaker.orca.kayenta.pipeline.RunCanaryPipelineStage
import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import retrofit2.mock.Calls

class MonitorKayentaCanaryTaskMessageTest {


  private val reportBase = "#/applications/myapp/canary/report/cfg1"
  private val documentationUrl = "https://docs.example.com/canary-documentation"

  private fun message(
    score: String,
    threshold: String,
    pass: Boolean = false,
    reportUrl: String? = null,
    documentation: Boolean = true
  ): String {
    val requirement = if (pass) "at or above the pass threshold" else "above the minimum accepted threshold"
    val sb = StringBuilder("Canary Analysis failed: final score is $score/100. It must be $requirement ($threshold/100) to pass.")
    if (reportUrl != null) {
      sb.append(" <a href=\"$reportUrl\" target=\"_blank\">View the report</a> to see a detailed breakdown.")
    }
    if (documentation) {
      sb.append(" Please check <a href=\"$documentationUrl\" target=\"_blank\">the canary analysis documentation</a> ")
      sb.append("for more details on how to manage your canary setup.")
    }
    return sb.toString()
  }

  private fun resultsWith(status: ExecutionStatus, score: Int, exception: Map<String, Any>? = null) = CanaryResults(
    true,
    status.toString(),
    CanaryResult(JudgeResult(JudgeScore(score, "", ""), emptyArray()), Duration.ofHours(1)),
    Instant.ofEpochMilli(1521059331684),
    Instant.ofEpochMilli(1521059331909),
    Instant.ofEpochMilli(1521059341101),
    "account name",
    "app name",
    null,
    exception
  )

  private fun execute(
    results: CanaryResults,
    marginal: Int,
    pass: Int,
    continuePipeline: Any? = null,
    template: String? = null,
    withIds: Boolean = false,
    documentation: String? = documentationUrl
  ): TaskResult {
    val service: KayentaService = Mockito.mock(KayentaService::class.java)
    `when`(service.getCanaryResults(Mockito.anyString(), Mockito.anyString())).thenAnswer { Calls.response(results) }
    `when`(service.getCredentials()).thenAnswer { Calls.response(listOf(KayentaCredential("foo", "bar"))) }
    val stage = pipeline {
      application = "myapp"
      stage {
        refId = "1"
        type = RunCanaryPipelineStage.STAGE_TYPE
        name = RunCanaryPipelineStage.STAGE_NAME_PREFIX
        context["canaryPipelineExecutionId"] = "run1"
        if (withIds) context["canaryConfigId"] = "cfg1"
        context["storageAccountName"] = "account name"
        context["metricsAccountName"] = "metrics name"
        context["scoreThresholds"] = Thresholds(pass = pass, marginal = marginal)
        if (continuePipeline != null) context["continuePipeline"] = continuePipeline
      }
    }.stageByRef("1")
    val config = KayentaCanaryReportProperties().apply {
      template?.let { urlTemplate = it }
      documentationUrl = documentation
    }
    return MonitorKayentaCanaryTask(service, CanaryReportLinks(config)).execute(stage)
  }

  private fun errorsOf(result: TaskResult): Any? =
    ((result.context["exception"] as? Map<*, *>)?.get("details") as? Map<*, *>)?.get("errors")

  @Test
  fun `marginal failures include score and threshold in message and exception`() {
    listOf(
      Triple(0, 50, message("0", "50")),
      Triple(33, 33, message("33", "33")),
      Triple(40, 50, message("40", "50"))
    ).forEach { (score, marginal, expected) ->
      val result = execute(resultsWith(ExecutionStatus.SUCCEEDED, score), marginal, 75)
      assertThat(result.status).isEqualTo(ExecutionStatus.TERMINAL)
      assertThat(result.context["canaryScoreMessage"]).isEqualTo(expected)
      assertThat(errorsOf(result)).isEqualTo(listOf(expected))
      assertThat(result.context["canaryScore"]).isEqualTo(score)
    }
  }

  @Test
  fun `continuePipeline true gives FAILED_CONTINUE, false or absent gives TERMINAL`() {
    val message = message("20", "50")
    val continued = execute(resultsWith(ExecutionStatus.SUCCEEDED, 20), 50, 75, true)
    assertThat(continued.status).isEqualTo(ExecutionStatus.FAILED_CONTINUE)
    assertThat(errorsOf(continued)).isEqualTo(listOf(message))
    val notContinued = execute(resultsWith(ExecutionStatus.SUCCEEDED, 20), 50, 75, false)
    assertThat(notContinued.status).isEqualTo(ExecutionStatus.TERMINAL)
    assertThat(errorsOf(notContinued)).isEqualTo(listOf(message))
  }

  @Test
  fun `scores above marginal succeed without an exception even if below pass`() {
    listOf(60, 51).forEach { score ->
      val result = execute(resultsWith(ExecutionStatus.SUCCEEDED, score), 50, 75, true)
      assertThat(result.status).isEqualTo(ExecutionStatus.SUCCEEDED)
      assertThat(result.context).doesNotContainKey("exception")
      assertThat(result.context).doesNotContainKey("canaryScoreMessage")
    }
  }

  @Test
  fun `appends the report and documentation sentences to both the message and the exception`() {
    val expected = message("20", "50", reportUrl = "$reportBase/run1")
    val result = execute(resultsWith(ExecutionStatus.SUCCEEDED, 20), 50, 75, withIds = true)
    assertThat(result.context["canaryScoreMessage"]).isEqualTo(expected)
    assertThat(errorsOf(result)).isEqualTo(listOf(expected))
  }

  @Test
  fun `uses a custom template`() {
    val result = execute(
      resultsWith(ExecutionStatus.SUCCEEDED, 20), 50, 75, template = "https://deck.example.com/{0}/{1}/{2}", withIds = true
    )
    assertThat(result.context["canaryScoreMessage"])
      .isEqualTo(message("20", "50", reportUrl = "https://deck.example.com/myapp/cfg1/run1"))
  }

  @Test
  fun `omits the report sentence but keeps the documentation sentence when the config id is missing`() {
    val expected = message("20", "50")
    val result = execute(resultsWith(ExecutionStatus.SUCCEEDED, 20), 50, 75, withIds = false)
    assertThat(result.context["canaryScoreMessage"]).isEqualTo(expected)
    assertThat(errorsOf(result)).isEqualTo(listOf(expected))
  }

  @Test
  fun `omits the documentation sentence when the documentation url is null or blank`() {
    listOf(null, "", "  ").forEach { documentation ->
      val withIds = execute(resultsWith(ExecutionStatus.SUCCEEDED, 20), 50, 75, withIds = true, documentation = documentation)
      assertThat(withIds.context["canaryScoreMessage"])
        .isEqualTo(message("20", "50", reportUrl = "$reportBase/run1", documentation = false))
      val neither = execute(resultsWith(ExecutionStatus.SUCCEEDED, 20), 50, 75, documentation = documentation)
      assertThat(neither.context["canaryScoreMessage"]).isEqualTo(message("20", "50", documentation = false))
      assertThat(errorsOf(neither)).isEqualTo(listOf(message("20", "50", documentation = false)))
    }
  }

  @Test
  fun `canceled and other halted statuses keep their exceptions`() {
    val canceled = execute(resultsWith(ExecutionStatus.CANCELED, 0), 50, 75, true)
    assertThat(canceled.status).isEqualTo(ExecutionStatus.TERMINAL)
    assertThat(errorsOf(canceled)).isEqualTo(listOf("Canary execution was canceled."))
    assertThat(canceled.context).doesNotContainKey("canaryScoreMessage")

    val exception = mapOf<String, Any>("details" to mapOf("errors" to listOf("boom")))
    val failed = execute(resultsWith(ExecutionStatus.TERMINAL, 0, exception), 50, 75)
    assertThat(failed.status).isEqualTo(ExecutionStatus.TERMINAL)
    assertThat(failed.context["exception"]).isEqualTo(exception)

    val running = execute(resultsWith(ExecutionStatus.RUNNING, 0), 50, 75)
    assertThat(running.status).isEqualTo(ExecutionStatus.RUNNING)
    assertThat(running.context).doesNotContainKey("exception")
  }

  @Test
  fun `renders whole numbers without decimals for score and threshold`() {
    val result = execute(resultsWith(ExecutionStatus.SUCCEEDED, 0), 80, 90)
    assertThat(result.context["canaryScoreMessage"]).isEqualTo(message("0", "80"))
  }

  @Test
  fun `a score equal to the marginal threshold fails with the marginal wording, never the pass wording`() {
    val result = execute(resultsWith(ExecutionStatus.SUCCEEDED, 76), 76, 90)
    assertThat(result.context["canaryScoreMessage"]).isEqualTo(message("76", "76"))
    assertThat(result.context["canaryScoreMessage"].toString()).doesNotContain("pass threshold")
  }
}
