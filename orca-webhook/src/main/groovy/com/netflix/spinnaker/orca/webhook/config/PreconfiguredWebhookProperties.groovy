/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.config

import groovy.transform.ToString
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpMethod

@ConfigurationProperties("webhook")
@ToString
class PreconfiguredWebhookProperties {

  public static List<String> ALL_FIELDS = PreconfiguredWebhook.declaredFields.findAll {
    !it.synthetic && !['props', 'enabled', 'label', 'description', 'type'].contains(it.name)
  }.collect { it.name }

  List<PreconfiguredWebhook> preconfigured = []

  @ToString(includeNames = true, includePackage = false)
  static class PreconfiguredWebhook {
    boolean enabled = true
    String label
    String description
    String type

    // Stage configuration fields (all optional):
    String url
    Map<String, List<String>> customHeaders
    HttpMethod method
    String payload
    Boolean waitForCompletion
    StatusUrlResolution statusUrlResolution
    String statusUrlJsonPath // if webhookResponse above
    String statusJsonPath
    String progressJsonPath
    String successStatuses
    String canceledStatuses
    String terminalStatuses

    List<String> getPreconfiguredProperties() {
      return ALL_FIELDS.findAll { this[it] != null }
    }

    boolean noUserConfigurableFields() {
      if (waitForCompletion == null) {
        return false
      } else if (waitForCompletion) {
        return getPreconfiguredProperties().size() >= ALL_FIELDS.size() - (statusUrlResolution == StatusUrlResolution.webhookResponse ? 0 : 1)
      } else {
        return ["url", "customHeaders", "method", "payload"].every { this[it] != null }
      }
    }
  }

  static enum StatusUrlResolution {
    getMethod, locationHeader, webhookResponse
  }

}
