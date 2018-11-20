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

package com.netflix.spinnaker.orca.webhook.pipeline

import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import spock.lang.Specification
import spock.lang.Subject

class PreconfiguredWebhookStageSpec extends Specification {

  def webhookService = Mock(WebhookService)
  def builder = new TaskNode.Builder()

  @Subject
  preconfiguredWebhookStage = new PreconfiguredWebhookStage(webhookService: webhookService)

  def "Context should be taken from PreconfiguredWebhookProperties"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), "webhook_1", [:])

    when:
    preconfiguredWebhookStage.taskGraph(stage, builder)

    then:
    1 * webhookService.preconfiguredWebhooks >> [createPreconfiguredWebhook("Webhook #1", "Description #1", "webhook_1")]
    stage.context == [
      url: "a",
      customHeaders: ["header": ["value1"]],
      method: HttpMethod.POST,
      payload: "b",
      waitForCompletion: true,
      statusUrlResolution: WebhookProperties.StatusUrlResolution.locationHeader,
      statusUrlJsonPath: "c",
      statusJsonPath: "d",
      progressJsonPath: "e",
      successStatuses: "f",
      canceledStatuses: "g",
      terminalStatuses: "h",
      parameterValues: null,
      permissions: null
    ]
  }

  def "Existing context should be preserved"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), "webhook_1", [
      url: "a",
      customHeaders: ["header": ["value1"]],
      method: HttpMethod.POST,
      payload: "b",
      waitForCompletion: true,
      statusUrlResolution: WebhookProperties.StatusUrlResolution.webhookResponse,
      statusUrlJsonPath: "c",
      statusJsonPath: "d",
      progressJsonPath: "e",
      successStatuses: "f",
      canceledStatuses: "g",
      terminalStatuses: "h",
      parameterValues: null,
      permissions: null
    ])

    when:
    preconfiguredWebhookStage.taskGraph(stage, builder)

    then:
    1 * webhookService.preconfiguredWebhooks >> [new WebhookProperties.PreconfiguredWebhook(label: "Webhook #1", description: "Description #1", type: "webhook_1")]
    stage.context == [
      url: "a",
      customHeaders: ["header": ["value1"]],
      method: HttpMethod.POST,
      payload: "b",
      waitForCompletion: true,
      statusUrlResolution: WebhookProperties.StatusUrlResolution.webhookResponse,
      statusUrlJsonPath: "c",
      statusJsonPath: "d",
      progressJsonPath: "e",
      successStatuses: "f",
      canceledStatuses: "g",
      terminalStatuses: "h",
      parameterValues: null,
      permissions: null
    ]
  }

  static WebhookProperties.PreconfiguredWebhook createPreconfiguredWebhook(def label, def description, def type) {
    def customHeaders = new HttpHeaders()
    customHeaders.add("header", "value1")
    return new WebhookProperties.PreconfiguredWebhook(
      label: label, description: description, type: type, url: "a", customHeaders: customHeaders, method: HttpMethod.POST, payload: "b",
      waitForCompletion: true, statusUrlResolution: WebhookProperties.StatusUrlResolution.locationHeader,
      statusUrlJsonPath: "c", statusJsonPath: "d", progressJsonPath: "e", successStatuses: "f", canceledStatuses: "g", terminalStatuses: "h"
    )
  }
}
