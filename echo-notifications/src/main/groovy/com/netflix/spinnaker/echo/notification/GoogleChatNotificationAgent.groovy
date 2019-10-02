/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.googlechat.GoogleChatMessage
import com.netflix.spinnaker.echo.googlechat.GoogleChatService
import com.netflix.spinnaker.echo.model.Event
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import retrofit.client.Response

import static net.logstash.logback.argument.StructuredArguments.kv
import static org.apache.commons.lang3.text.WordUtils.capitalize

@Slf4j
@Service
@ConditionalOnProperty("googlechat.enabled")
public class GoogleChatNotificationAgent extends AbstractEventNotificationAgent {

  @Autowired
  GoogleChatService googleChatService;

  @Override
  public void sendNotifications(Map preference, String application, Event event, Map config, String status) {
    String buildInfo = ''

    if (config.type == 'pipeline' || config.type == 'stage') {
      if (event.content?.execution?.trigger?.buildInfo?.url) {
        buildInfo = """build #<a href="${event.content.execution.trigger.buildInfo.url}">${
          event.content.execution.trigger.buildInfo.number as Integer
        }</a> """
      }
    }

    log.info('Sending Google Chat message {} for {} {} {} {}', kv('address', preference.address), kv('application', application), kv('type', config.type), kv('status', status), kv('executionId', event.content?.execution?.id))

    String body = ''

    if (config.type == 'stage') {
      String stageName = event.content.name ?: event.content?.context?.stageDetails?.name
      body = """Stage $stageName for """
    }

    String link = "${spinnakerUrl}/#/applications/${application}/${config.type == 'stage' ? 'executions/details' : config.link }/${event.content?.execution?.id}"

    body +=
      """${capitalize(application)}'s <a href="${link}">${
        event.content?.execution?.name ?: event.content?.execution?.description
      }</a> ${buildInfo}${config.type == 'task' ? 'task' : 'pipeline'} ${status == 'starting' ? 'is' : 'has'} ${
        status == 'complete' ? 'completed successfully' : status
      }"""

    if (preference.message?."$config.type.$status"?.text) {
      body += "\n\n" + preference.message."$config.type.$status".text
    }

    String customMessage = preference.customMessage ?: event.content?.context?.customMessage
    if (customMessage) {
      body = customMessage
        .replace("{{executionId}}", (String) event.content.execution?.id ?: "")
        .replace("{{link}}", link ?: "")
    }

    // In Chat, users can only copy the whole link easily. We just extract the information from the whole link.
    // Example: https://chat.googleapis.com/v1/spaces/{partialWebhookUrl}
    String baseUrl = "https://chat.googleapis.com/v1/spaces/"
    String completeLink = preference.address
    String partialWebhookURL = completeLink.substring(baseUrl.length())
    Response response = googleChatService.sendMessage(partialWebhookURL, new GoogleChatMessage(body))

    log.info("Received response from Google Chat: {} {} for execution id {}. {}",
      response?.status, response?.reason, event.content?.execution?.id, response?.body)
  }

  @Override
  public String getNotificationType() {
    return "googlechat";
  }
}

