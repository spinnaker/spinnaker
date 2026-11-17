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
import java.net.URLEncoder
import java.text.MessageFormat
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Builds canary score failure messages. The message always states the final score and the threshold it missed;
 * it then adds a "View the report" sentence when a report URL can be built and a canary documentation sentence when a
 * documentation URL is configured.
 *
 * The report URL is a MessageFormat template from [KayentaCanaryReportProperties]
 * (`kayenta.canary-report.url-template`) with {0}=application, {1}=canaryConfigId, {2}=canaryPipelineExecutionId.
 * The documentation URL comes from `kayenta.canary-report.documentation-url`.
 */
@Component
class CanaryReportLinks(
  private val properties: KayentaCanaryReportProperties
) {

  private val log = LoggerFactory.getLogger(javaClass)

  /** Renders the report URL, or null if any identifier is missing/blank or the template is unusable. */
  fun reportUrl(application: String?, configId: Any?, runId: Any?): String? {
    val app = application?.takeIf { it.isNotBlank() }
    val config = (configId as? String)?.takeIf { it.isNotBlank() }
    val run = (runId as? String)?.takeIf { it.isNotBlank() }
    if (app == null || config == null || run == null) {
      return null
    }
    val template = properties.urlTemplate
    if (template.isNullOrBlank()) {
      return null
    }
    return try {
      MessageFormat.format(template, encode(app), encode(config), encode(run))
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid canary report URL template '{}' configured at kayenta.canary-report.url-template", template, e)
      null
    }
  }

  /** Which threshold a score failed, which determines how the requirement is worded. */
  enum class ThresholdKind(val requirement: String) {
    /** The score must be strictly above the marginal threshold (a score equal to it fails). */
    MARGINAL("above the minimum accepted threshold"),

    /** The score must be at or above the pass threshold (a score equal to it passes). */
    PASS("at or above the pass threshold")
  }

  /**
   * Builds "Canary Analysis failed: final score is S/100. It must be [kind requirement] (T/100) to pass." and then,
   * each only when available:
   * - ` <a href="REPORT_URL" target="_blank">View the report</a> to see a detailed breakdown.` when a report URL can
   *   be built;
   * - ` Please check <a href="DOCUMENTATION_URL" target="_blank">the canary analysis documentation</a> for more
   *   details on how to manage your canary setup.` when a documentation URL is configured.
   * With neither, the message is just the first two sentences.
   */
  fun scoreFailureMessage(
    score: Number,
    threshold: Number,
    kind: ThresholdKind,
    application: String?,
    configId: Any?,
    runId: Any?
  ): String {
    val message = StringBuilder(
      "Canary Analysis failed: final score is ${formatScore(score)}/100. " +
        "It must be ${kind.requirement} (${formatScore(threshold)}/100) to pass."
    )
    reportUrl(application, configId, runId)?.let {
      message.append(" <a href=\"${escapeAttribute(it)}\" target=\"_blank\">View the report</a> to see a detailed breakdown.")
    }
    properties.documentationUrl?.takeIf { it.isNotBlank() }?.let {
      message.append(
        " Please check <a href=\"${escapeAttribute(it)}\" target=\"_blank\">the canary analysis documentation</a> " +
          "for more details on how to manage your canary setup."
      )
    }
    return message.toString()
  }

  /** Renders whole numbers without decimals ("80", "0") and keeps fractions otherwise ("42.5"). */
  private fun formatScore(value: Number): String {
    val d = value.toDouble()
    return if (d.isFinite() && d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else value.toString()
  }

  /** Escapes a value for use inside a double-quoted HTML attribute. */
  private fun escapeAttribute(value: String) = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

  private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
