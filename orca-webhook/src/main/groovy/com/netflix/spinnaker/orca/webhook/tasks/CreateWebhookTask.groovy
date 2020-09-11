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

package com.netflix.spinnaker.orca.webhook.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class CreateWebhookTask implements RetryableTask {

  long backoffPeriod = 10000
  long timeout = 300000

  WebhookService webhookService
  WebhookProperties webhookProperties
  ObjectMapper objectMapper

  @Autowired
  CreateWebhookTask(WebhookService webhookService, WebhookProperties webhookProperties, ObjectMapper objectMapper) {
    this.webhookService = webhookService
    this.webhookProperties = webhookProperties
    this.objectMapper = objectMapper
  }

  @Override
  TaskResult execute(StageExecution stage) {
    WebhookStage.StageData stageData = stage.mapTo(WebhookStage.StageData)

    WebhookResponseProcessor responseProcessor = new WebhookResponseProcessor(objectMapper, stage, webhookProperties)

    try {
      def response = webhookService.exchange(stageData.method, stageData.url, stageData.payload, stageData.customHeaders)
      return responseProcessor.process(response, null)
    } catch (Exception e) {
      return responseProcessor.process(null, e)
    }

  }

}
