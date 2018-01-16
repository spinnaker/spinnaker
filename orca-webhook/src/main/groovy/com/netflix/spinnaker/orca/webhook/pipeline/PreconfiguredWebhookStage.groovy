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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.config.PreconfiguredWebhookProperties.PreconfiguredWebhook
import com.netflix.spinnaker.orca.webhook.exception.PreconfiguredWebhookNotFoundException
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PreconfiguredWebhookStage extends WebhookStage {

  @Autowired
  private WebhookService webhookService

  def fields = PreconfiguredWebhook.declaredFields.findAll {
    !it.synthetic && !['props', 'enabled', 'label', 'description', 'type', 'parameters'].contains(it.name)
  }.collect { it.name }

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    def preconfiguredWebhook = webhookService.getPreconfiguredWebhooks().find { stage.type == it.type }

    if (!preconfiguredWebhook) {
      throw new PreconfiguredWebhookNotFoundException((String) stage.type)
    }

    stage.setContext(overrideIfNotSetInContextAndOverrideDefault(stage.context, preconfiguredWebhook))
    super.taskGraph(stage, builder)
  }

  private Map<String, Object> overrideIfNotSetInContextAndOverrideDefault(Map<String, Object> context, PreconfiguredWebhook preconfiguredWebhook) {
    fields.each {
      if (context[it] == null || preconfiguredWebhook[it] != null) {
        context[it] = preconfiguredWebhook[it]
      }
    }
    return context
  }
}
