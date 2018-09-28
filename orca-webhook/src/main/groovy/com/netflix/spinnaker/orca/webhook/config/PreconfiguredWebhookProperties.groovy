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
import groovy.util.logging.Slf4j
import org.springframework.boot.context.properties.ConfigurationProperties
import com.netflix.spinnaker.fiat.model.resources.Role;
import org.springframework.http.HttpMethod

@ConfigurationProperties("webhook")
@ToString
@Slf4j
class PreconfiguredWebhookProperties {

  public static List<String> ALL_FIELDS = PreconfiguredWebhook.declaredFields.findAll {
    !it.synthetic && !['props', 'enabled', 'label', 'description', 'type', 'parameters', 'parameterValues', 'permissions'].contains(it.name)
  }.collect { it.name }

  List<PreconfiguredWebhook> preconfigured = []

  @ToString(includeNames = true, includePackage = false)
  static class PreconfiguredWebhook {
    boolean enabled = true
    String label
    String description
    String type
    List<WebhookParameter> parameters

    // Stage configuration fields (all optional):
    String url
    Map<String, List<String>> customHeaders
    Map<String, String> parameterValues
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
    Map<String, List<String>> permissions


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

    boolean isAllowed(String permission, Set<Role.View> roles) {
      if (permissions && permissions.containsKey(permission) && permissions[permission]) {
        def allowedRoles = permissions[permission].findAll { roles*.getName().contains(it.toString()) }
        return allowedRoles.size() > 0
      }
      return true
    }
  }

  static class WebhookParameter {
    String name
    String label
    String defaultValue
    String description
    ParameterType type = ParameterType.string
    int order

    static enum ParameterType {
      string
    }
  }

  static enum StatusUrlResolution {
    getMethod, locationHeader, webhookResponse
  }

}
