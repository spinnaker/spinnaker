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
import com.netflix.spinnaker.orca.kayenta.tasks.CanaryReportLinks.ThresholdKind.MARGINAL
import com.netflix.spinnaker.orca.kayenta.tasks.CanaryReportLinks.ThresholdKind.PASS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CanaryReportLinksTest {

  private val base = "Canary Analysis failed: final score is 0/100. " +
    "It must be above the minimum accepted threshold (80/100) to pass."
  private val defaultUrl = "#/applications/myapp/canary/report/cfg1/run1"
  private val documentationUrl = "https://docs.example.com/canary-documentation"
  private val reportSentence = " <a href=\"$defaultUrl\" target=\"_blank\">View the report</a> to see a detailed breakdown."
  private val documentationSentence = " Please check <a href=\"$documentationUrl\" target=\"_blank\">the canary analysis documentation</a> " +
    "for more details on how to manage your canary setup."

  private fun links(template: String? = null, documentation: String? = documentationUrl) =
    CanaryReportLinks(
      KayentaCanaryReportProperties().apply {
        template?.let { urlTemplate = it }
        documentationUrl = documentation
      }
    )

  private val defaultLinks = links()

  @Test
  fun `uses the default template and no documentation url when none are configured`() {
    assertThat(defaultLinks.reportUrl("myapp", "cfg1", "run1")).isEqualTo(defaultUrl)
    assertThat(KayentaCanaryReportProperties().documentationUrl).isNull()
  }

  @Test
  fun `renders a custom template with application, config id and run id`() {
    val custom = links("https://deck.example.com/#/a/{0}/c/{1}/r/{2}")
    assertThat(custom.reportUrl("myapp", "cfg1", "run1")).isEqualTo("https://deck.example.com/#/a/myapp/c/cfg1/r/run1")
  }

  @Test
  fun `message with both links has the report sentence then the documentation sentence`() {
    assertThat(defaultLinks.scoreFailureMessage(0, 80, MARGINAL, "myapp", "cfg1", "run1"))
      .isEqualTo(base + reportSentence + documentationSentence)
  }

  @Test
  fun `marginal and pass thresholds use different wording`() {
    val noLinks = links(documentation = null)
    assertThat(noLinks.scoreFailureMessage(76, 80, MARGINAL, null, null, null)).isEqualTo(
      "Canary Analysis failed: final score is 76/100. It must be above the minimum accepted threshold (80/100) to pass."
    )
    assertThat(noLinks.scoreFailureMessage(76, 80, PASS, null, null, null)).isEqualTo(
      "Canary Analysis failed: final score is 76/100. It must be at or above the pass threshold (80/100) to pass."
    )
  }

  @Test
  fun `report url absent but documentation present gives the documentation sentence only`() {
    assertThat(defaultLinks.scoreFailureMessage(0, 80, MARGINAL, "myapp", null, "run1"))
      .isEqualTo(base + documentationSentence)
  }

  @Test
  fun `report url present but documentation null or blank gives the report sentence only`() {
    listOf(null, "", "   ").forEach { documentation ->
      assertThat(links(documentation = documentation).scoreFailureMessage(0, 80, MARGINAL, "myapp", "cfg1", "run1"))
        .isEqualTo(base + reportSentence)
    }
  }

  @Test
  fun `neither link gives just the first two sentences`() {
    assertThat(links(documentation = null).scoreFailureMessage(0, 80, MARGINAL, null, null, null)).isEqualTo(base)
    assertThat(links(documentation = " ").scoreFailureMessage(0, 80, MARGINAL, "myapp", "cfg1", null)).isEqualTo(base)
  }

  @Test
  fun `escapes ampersands in a template with a query string and quotes the href`() {
    val custom = links("https://deck.example.com/#/applications/{0}/canary/report/{1}/{2}?count=20&x=1", documentation = null)
    assertThat(custom.scoreFailureMessage(0, 80, MARGINAL, "myapp", "cfg1", "run1")).isEqualTo(
      "$base <a href=\"https://deck.example.com/#/applications/myapp/canary/report/cfg1/run1?count=20&amp;x=1\" " +
        "target=\"_blank\">View the report</a> to see a detailed breakdown."
    )
  }

  @Test
  fun `a template containing quotes or angle brackets cannot break out of the href attribute`() {
    val custom = links("https://x/{0}\" onclick=\"evil()\"><script>/{1}/{2}", documentation = null)
    val result = custom.scoreFailureMessage(0, 80, MARGINAL, "myapp", "cfg1", "run1")
    assertThat(result).isEqualTo(
      "$base <a href=\"https://x/myapp&quot; onclick=&quot;evil()&quot;&gt;&lt;script&gt;/cfg1/run1\" " +
        "target=\"_blank\">View the report</a> to see a detailed breakdown."
    )
    assertThat(result).doesNotContain("<script>").doesNotContain("\" onclick")
  }

  @Test
  fun `escapes the documentation url the same way`() {
    val custom = links(documentation = "https://docs.example.com/c?a=1&b=2\" onclick=\"evil()\"><script>")
    val result = custom.scoreFailureMessage(0, 80, MARGINAL, null, null, null)
    assertThat(result).isEqualTo(
      "$base Please check <a href=\"https://docs.example.com/c?a=1&amp;b=2&quot; onclick=&quot;evil()&quot;" +
        "&gt;&lt;script&gt;\" target=\"_blank\">the canary analysis documentation</a> " +
        "for more details on how to manage your canary setup."
    )
    assertThat(result).doesNotContain("<script>").doesNotContain("\" onclick")
  }

  @Test
  fun `omits the report sentence when an identifier is missing, blank or not a string`() {
    listOf(
      listOf(null, "cfg1", "run1"),
      listOf("", "cfg1", "run1"),
      listOf("myapp", null, "run1"),
      listOf("myapp", " ", "run1"),
      listOf("myapp", "cfg1", null),
      listOf("myapp", "cfg1", 42)
    ).forEach { (app, config, run) ->
      assertThat(defaultLinks.scoreFailureMessage(0, 80, MARGINAL, app as String?, config, run))
        .isEqualTo(base + documentationSentence)
    }
  }

  @Test
  fun `url-encodes path segments and does not apply number formatting`() {
    assertThat(defaultLinks.reportUrl("my app", "cfg/1?x=y#z", "run 1+2"))
      .isEqualTo("#/applications/my%20app/canary/report/cfg%2F1%3Fx%3Dy%23z/run%201%2B2")
    assertThat(defaultLinks.reportUrl("myapp", "cfg1", "1234567"))
      .isEqualTo("#/applications/myapp/canary/report/cfg1/1234567")
  }

  @Test
  fun `omits the report sentence for a blank or malformed template`() {
    assertThat(links("  ").scoreFailureMessage(0, 80, MARGINAL, "myapp", "cfg1", "run1")).isEqualTo(base + documentationSentence)
    assertThat(links("https://x/{0").scoreFailureMessage(0, 80, MARGINAL, "myapp", "cfg1", "run1"))
      .isEqualTo(base + documentationSentence)
  }

  @Test
  fun `formats whole and fractional scores and thresholds of any numeric type`() {
    val noLinks = links(documentation = null)
    fun message(score: Number, threshold: Number) = noLinks.scoreFailureMessage(score, threshold, MARGINAL, null, null, null)
    fun expected(score: String, threshold: String) =
      "Canary Analysis failed: final score is $score/100. It must be above the minimum accepted threshold ($threshold/100) to pass."
    assertThat(message(0, 80)).isEqualTo(expected("0", "80"))
    assertThat(message(0.0, 80.0)).isEqualTo(expected("0", "80"))
    assertThat(message(42.5, 50)).isEqualTo(expected("42.5", "50"))
    assertThat(message(60L, 79.5)).isEqualTo(expected("60", "79.5"))
    assertThat(message(-0.0, 80)).isEqualTo(expected("0", "80"))
  }
}
