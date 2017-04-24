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
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.config.PreconfiguredWebhookProperties
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
    def stage = new Stage<Pipeline>(new Pipeline(), "webhook_1", [:])

    when:
    preconfiguredWebhookStage.taskGraph(stage, builder)

    then:
    1 * webhookService.preconfiguredWebhooks >> [createPreconfiguredWebhook("Webhook #1", "Description #1", "webhook_1")]
    stage.context == [
      url: "a",
      headers: ["header": ["value1"]],
      method: HttpMethod.POST,
      payload: "b",
      waitForCompletion: true,
      statusUrlResolution: PreconfiguredWebhookProperties.StatusUrlResolution.webhookResponse,
      statusUrlJsonPath: "c",
      statusJsonPath: "d",
      progressJsonPath: "e",
      successStatuses: "f",
      canceledStatuses: "g",
      terminalStatuses: "h"
    ]
  }

  def "Existing context should be preserved"() {
    given:
    def stage = new Stage<Pipeline>(new Pipeline(), "webhook_1", [
      url: "a",
      headers: ["header": ["value1"]],
      method: HttpMethod.POST,
      payload: "b",
      waitForCompletion: true,
      statusUrlResolution: PreconfiguredWebhookProperties.StatusUrlResolution.webhookResponse,
      statusUrlJsonPath: "c",
      statusJsonPath: "d",
      progressJsonPath: "e",
      successStatuses: "f",
      canceledStatuses: "g",
      terminalStatuses: "h"
    ])

    when:
    preconfiguredWebhookStage.taskGraph(stage, builder)

    then:
    1 * webhookService.preconfiguredWebhooks >> [new PreconfiguredWebhookProperties.PreconfiguredWebhook(label: "Webhook #1", description: "Description #1", type: "webhook_1")]
    stage.context == [
      url: "a",
      headers: ["header": ["value1"]],
      method: HttpMethod.POST,
      payload: "b",
      waitForCompletion: true,
      statusUrlResolution: PreconfiguredWebhookProperties.StatusUrlResolution.webhookResponse,
      statusUrlJsonPath: "c",
      statusJsonPath: "d",
      progressJsonPath: "e",
      successStatuses: "f",
      canceledStatuses: "g",
      terminalStatuses: "h"
    ]
  }

  def "Should prioritize user context to preconfigured context to allow overriding values in advanced use cases"() {
    given:
    def stage = new Stage<Pipeline>(new Pipeline(), "webhook_1", [
      url: "fromContext",
      headers: ["fromContext": ["fromContext"]],
      method: HttpMethod.POST,
      waitForCompletion: false,
      statusUrlResolution: PreconfiguredWebhookProperties.StatusUrlResolution.locationHeader,
      statusUrlJsonPath: "fromContext",
      statusJsonPath: "fromContext",
      progressJsonPath: "fromContext",
      successStatuses: "fromContext",
      canceledStatuses: "fromContext",
      terminalStatuses: "fromContext"
    ])

    when:
    preconfiguredWebhookStage.taskGraph(stage, builder)

    then:
    1 * webhookService.preconfiguredWebhooks >> [createPreconfiguredWebhook("Webhook #1", "Description #1", "webhook_1")]
    stage.context == [
      url: "fromContext",
      headers: ["fromContext": ["fromContext"]],
      method: HttpMethod.POST,
      payload: "b",
      waitForCompletion: false,
      statusUrlResolution: PreconfiguredWebhookProperties.StatusUrlResolution.locationHeader,
      statusUrlJsonPath: "fromContext",
      statusJsonPath: "fromContext",
      progressJsonPath: "fromContext",
      successStatuses: "fromContext",
      canceledStatuses: "fromContext",
      terminalStatuses: "fromContext"
    ]
  }

  static PreconfiguredWebhookProperties.PreconfiguredWebhook createPreconfiguredWebhook(def label, def description, def type) {
    def headers = new HttpHeaders()
    headers.add("header", "value1")
    return new PreconfiguredWebhookProperties.PreconfiguredWebhook(
      label: label, description: description, type: type, url: "a", headers: headers, method: HttpMethod.POST, payload: "b",
      waitForCompletion: true, statusUrlResolution: PreconfiguredWebhookProperties.StatusUrlResolution.webhookResponse,
      statusUrlJsonPath: "c", statusJsonPath: "d", progressJsonPath: "e", successStatuses: "f", canceledStatuses: "g", terminalStatuses: "h"
    )
  }
}
