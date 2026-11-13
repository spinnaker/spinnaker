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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.kayenta.pipeline.KayentaCanaryStage
import com.netflix.spinnaker.orca.kayenta.pipeline.RunCanaryPipelineStage
import java.time.Duration
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AggregateCanaryResultsTaskMessageTest {

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

  private fun executeWith(
    scores: List<Double>,
    scoreThresholds: Map<String, Any>,
    template: String? = null,
    withIds: Boolean = false,
    documentation: String? = documentationUrl
  ): TaskResult {
    val pipeline = pipeline {
      application = "myapp"
      scores.forEachIndexed { i, score ->
        stage {
          refId = "1<$i"
          type = RunCanaryPipelineStage.STAGE_TYPE
          name = "${RunCanaryPipelineStage.STAGE_NAME_PREFIX}${i + 1}"
          context["canaryScore"] = score
          if (withIds) {
            context["canaryConfigId"] = "cfg1"
            context["canaryPipelineExecutionId"] = "run${i + 1}"
          }
        }
      }
      stage {
        refId = "1"
        type = KayentaCanaryStage.STAGE_TYPE
        name = "kayentaCanary"
        context["canaryConfig"] = mapOf(
          "canaryConfigId" to UUID.randomUUID().toString(),
          "scopes" to listOf(mapOf("controlScope" to "myapp-v010", "experimentScope" to "myapp-v021")),
          "scoreThresholds" to scoreThresholds,
          "lifetimeDuration" to Duration.parse("PT1H")
        )
      }
    }
    val parent = pipeline.stageByRef("1")
    pipeline.stages.filter { it !== parent }.forEach { it.parentStageId = parent.id }
    parent.context["intervalStageId"] = parent.id
    val config = KayentaCanaryReportProperties().apply {
      template?.let { urlTemplate = it }
      documentationUrl = documentation
    }
    return AggregateCanaryResultsTask(CanaryReportLinks(config)).execute(parent)
  }

  private fun errorsOf(result: TaskResult): Any? =
    ((result.context["exception"] as? Map<*, *>)?.get("details") as? Map<*, *>)?.get("errors")

  @Test
  fun `marginal failures report score and threshold with the marginal wording`() {
    listOf(
      Triple(0.0, mapOf("marginal" to 50, "pass" to 75), message("0", "50")),
      Triple(50.0, mapOf("marginal" to 50, "pass" to 75), message("50", "50")),
      Triple(42.5, mapOf("marginal" to 50), message("42.5", "50"))
    ).forEach { (score, thresholds, message) ->
      val result = executeWith(listOf(score), thresholds)
      assertThat(result.status).isEqualTo(TERMINAL)
      assertThat(result.context["canaryScoreMessage"]).isEqualTo(message)
      assertThat(errorsOf(result)).isEqualTo(listOf(message))
    }
  }

  @Test
  fun `pass failures report score and threshold with the pass wording`() {
    listOf(
      Triple(60.0, mapOf("marginal" to 50, "pass" to 75), message("60", "75", pass = true)),
      Triple(74.9, mapOf("marginal" to 50, "pass" to 75), message("74.9", "75", pass = true)),
      Triple(10.5, mapOf("pass" to 70), message("10.5", "70", pass = true))
    ).forEach { (score, thresholds, message) ->
      val result = executeWith(listOf(score), thresholds)
      assertThat(result.status).isEqualTo(TERMINAL)
      assertThat(result.context["canaryScoreMessage"]).isEqualTo(message)
      assertThat(errorsOf(result)).isEqualTo(listOf(message))
    }
  }

  @Test
  fun `successful and neutral outcomes never carry an exception`() {
    // Absent thresholds deserialize to 0 (Thresholds fields are non-null Ints), so any positive score passes.
    val noThresholds = executeWith(listOf(10.0), emptyMap())
    assertThat(noThresholds.status).isEqualTo(SUCCEEDED)
    assertThat(noThresholds.context).doesNotContainKey("exception")

    val aboveMarginalOnly = executeWith(listOf(51.0), mapOf("marginal" to 50))
    assertThat(aboveMarginalOnly.status).isEqualTo(SUCCEEDED)
    assertThat(aboveMarginalOnly.context).doesNotContainKey("exception")

    val equalPass = executeWith(listOf(75.0), mapOf("marginal" to 50, "pass" to 75))
    assertThat(equalPass.status).isEqualTo(SUCCEEDED)
    assertThat(equalPass.context["canaryScoreMessage"])
      .isEqualTo("Final canary score 75.0 met or exceeded the pass score threshold.")
    assertThat(equalPass.context).doesNotContainKey("exception")

    val abovePass = executeWith(listOf(90.5), mapOf("marginal" to 50, "pass" to 75), withIds = true)
    assertThat(abovePass.status).isEqualTo(SUCCEEDED)
    assertThat(abovePass.context).doesNotContainKey("exception")
    assertThat(abovePass.context["canaryScoreMessage"].toString())
      .doesNotContain("View the report")
      .doesNotContain("<a href")
  }

  @Test
  fun `a zero score with no thresholds fails against the implicit zero marginal`() {
    // Pre-existing behavior: missing thresholds become 0, and 0.0 <= 0.
    val result = executeWith(listOf(0.0), emptyMap())
    assertThat(result.status).isEqualTo(TERMINAL)
    assertThat(result.context["canaryScoreMessage"]).isEqualTo(message("0", "0"))
  }

  @Test
  fun `links the last run canary stage in both message and exception`() {
    val marginalMessage = message("20", "50", reportUrl = "$reportBase/run2")
    val result = executeWith(listOf(80.0, 20.0), mapOf("marginal" to 50, "pass" to 75), withIds = true)
    assertThat(result.status).isEqualTo(TERMINAL)
    assertThat(result.context["canaryScoreMessage"]).isEqualTo(marginalMessage)
    assertThat(errorsOf(result)).isEqualTo(listOf(marginalMessage))

    val passMessage = message("60", "75", pass = true, reportUrl = "$reportBase/run1")
    val passResult = executeWith(listOf(60.0), mapOf("marginal" to 50, "pass" to 75), withIds = true)
    assertThat(passResult.context["canaryScoreMessage"]).isEqualTo(passMessage)
    assertThat(errorsOf(passResult)).isEqualTo(listOf(passMessage))
  }

  @Test
  fun `uses a custom template`() {
    val result = executeWith(
      listOf(20.0), mapOf("marginal" to 50), template = "https://deck.example.com/{0}/{1}/{2}", withIds = true
    )
    assertThat(result.context["canaryScoreMessage"])
      .isEqualTo(message("20", "50", reportUrl = "https://deck.example.com/myapp/cfg1/run1"))
  }

  @Test
  fun `omits the report sentence but keeps the documentation sentence when ids are missing`() {
    val expected = message("20", "50")
    val result = executeWith(listOf(20.0), mapOf("marginal" to 50), withIds = false)
    assertThat(result.context["canaryScoreMessage"]).isEqualTo(expected)
    assertThat(errorsOf(result)).isEqualTo(listOf(expected))
  }

  @Test
  fun `omits the documentation sentence when the documentation url is null or blank`() {
    listOf(null, "", "  ").forEach { documentation ->
      val withIds = executeWith(listOf(20.0), mapOf("marginal" to 50), withIds = true, documentation = documentation)
      assertThat(withIds.context["canaryScoreMessage"])
        .isEqualTo(message("20", "50", reportUrl = "$reportBase/run1", documentation = false))
      val withoutIds = executeWith(listOf(60.0), mapOf("pass" to 80), documentation = documentation)
      assertThat(withoutIds.context["canaryScoreMessage"])
        .isEqualTo(message("60", "80", pass = true, documentation = false))
      assertThat(errorsOf(withoutIds)).isEqualTo(listOf(message("60", "80", pass = true, documentation = false)))
    }
  }

  @Test
  fun `renders whole-number scores without decimals and keeps fractions`() {
    listOf(0.0 to "0", 79.0 to "79", 49.99 to "49.99", 42.5 to "42.5").forEach { (score, rendered) ->
      val result = executeWith(listOf(score), mapOf("marginal" to 80))
      assertThat(result.context["canaryScoreMessage"]).isEqualTo(message(rendered, "80"))
    }
  }

  @Test
  fun `pass failures link the report and omit the link without ids`() {
    val withLink = executeWith(listOf(60.0), mapOf("pass" to 80), withIds = true)
    assertThat(withLink.context["canaryScoreMessage"])
      .isEqualTo(message("60", "80", pass = true, reportUrl = "$reportBase/run1"))
    val withoutLink = executeWith(listOf(60.0), mapOf("pass" to 80))
    assertThat(withoutLink.context["canaryScoreMessage"]).isEqualTo(message("60", "80", pass = true))
  }
}
