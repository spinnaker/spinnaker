/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.hipchat.HipchatMessage
import com.netflix.spinnaker.echo.hipchat.HipchatService
import com.netflix.spinnaker.echo.model.Event
import groovy.util.logging.Slf4j
import org.apache.commons.lang.WordUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

import static net.logstash.logback.argument.StructuredArguments.kv

@Slf4j
@ConditionalOnProperty('hipchat.enabled')
@Service
class HipchatNotificationAgent extends AbstractEventNotificationAgent {

  @Autowired
  HipchatService hipchatService

  @Value('${hipchat.token}')
  String token

  @Override
  void sendNotifications(Map preference, String application, Event event, Map config, String status) {
    try {
      boolean notify = false
      if (status == 'failed') {
        notify = true
      }

      String color = 'gray'

      if (status == 'failed') {
        color = 'red'
      }

      if (status == 'complete') {
        color = 'green'
      }

      String buildInfo = ''

      if (config.type == 'pipeline' || config.type == 'stage') {
        if (event.content?.execution?.trigger?.buildInfo?.url) {
          buildInfo = """build #<a href="${event.content.execution.trigger.buildInfo.url}">${
            event.content.execution.trigger.buildInfo.number as Integer
          }</a> """
        }
      }

      log.info('Sending Hipchat message {} for {} {} {} {}', kv('address', preference.address), kv('application', application), kv('type', config.type), kv('status', status), kv('executionId', event.content?.execution?.id))


      String message = ''

      if (config.type == 'stage') {
        String stageName = event.content.name ?: event.content?.context?.stageDetails?.name
        message = """Stage $stageName for """
      }

      String link = "${spinnakerUrl}/#/applications/${application}/${config.type == 'stage' ? 'executions/details' : config.link }/${event.content?.execution?.id}"

      message +=
        """${WordUtils.capitalize(application)}'s <a href="${link}">${
          event.content?.execution?.name ?: event.content?.execution?.description
        }</a> ${buildInfo} ${config.type == 'task' ? 'task' : 'pipeline'} ${status == 'starting' ? 'is' : 'has'} ${
          status == 'complete' ? 'completed successfully' : status
        }"""

      String customMessage = event.content?.context?.customMessage
      if (customMessage) {
        message = customMessage
          .replace("{{executionId}}", (String) event.content.execution?.id ?: "")
          .replace("{{link}}", link ?: "")
      }

      hipchatService.sendMessage(
        token,
        preference.address,
        new HipchatMessage(
          message: message,
          color: color,
          notify: notify
        )
      )

    } catch (Exception e) {
      log.error('failed to send hipchat message ', e)
    }
  }

  @Override
  String getNotificationType() {
    'hipchat'
  }

}

