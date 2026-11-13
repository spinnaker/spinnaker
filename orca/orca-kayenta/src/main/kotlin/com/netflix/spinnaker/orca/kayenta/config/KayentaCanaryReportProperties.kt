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

package com.netflix.spinnaker.orca.kayenta.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Settings for the links ("View the report" and the canary analysis documentation) added to canary score failure
 * messages. Bound from `kayenta.canary-report.*`.
 */
@ConfigurationProperties("kayenta.canary-report")
class KayentaCanaryReportProperties {
  /**
   * MessageFormat URL template: {0}=application, {1}=canaryConfigId, {2}=canaryPipelineExecutionId.
   * The default is a Deck-relative link, so it resolves against the Deck instance showing the message.
   */
  var urlTemplate: String = DEFAULT_URL_TEMPLATE

  /**
   * URL of the canary analysis documentation linked from score failure messages. Null (the default) or blank omits the
   * documentation sentence.
   */
  var documentationUrl: String? = null

  companion object {
    const val DEFAULT_URL_TEMPLATE = "#/applications/{0}/canary/report/{1}/{2}"
  }
}
