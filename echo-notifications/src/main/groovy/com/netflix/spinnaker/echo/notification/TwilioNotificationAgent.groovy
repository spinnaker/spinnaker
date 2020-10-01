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

import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.twilio.TwilioService
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.text.WordUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Slf4j
@ConditionalOnProperty('twilio.enabled')
@Service
class TwilioNotificationAgent extends AbstractEventNotificationAgent {

  @Autowired
  TwilioService twilioService

  @Value('${twilio.account}')
  String account

  @Value('${twilio.from}')
  String from

  @Override
  void sendNotifications(Map<String, Object> preference, String application, Event event, Map<String, String> config, String status) {
    String name = event.content?.execution?.name ?: event.content?.execution?.description
    String link = "${spinnakerUrl}/#/applications/${application}/${config.type == 'stage' ? 'executions/details' : config.link}/${event.content?.execution?.id}"

    String buildInfo = ''

    if (config.type == 'pipeline') {
      if (event.content?.execution?.trigger?.buildInfo?.url) {
        buildInfo = """build #${event.content.execution.trigger.buildInfo.number as Integer} """
      }
    }

    log.info("Twilio: sms for ${preference.address} - ${link}")

    String message = ''

    if (config.type == 'stage') {
      String stageName = event.content.name ?: event.content?.context?.stageDetails?.name
      message = """Stage $stageName for """
    }

    message +=
      """${WordUtils.capitalize(application)}'s ${
        event.content?.execution?.name ?: event.content?.execution?.description
      } ${buildInfo} ${config.type == 'task' ? 'task' : 'pipeline'} ${buildInfo} ${
        status == 'starting' ? 'is' : 'has'
      } ${
        status == 'complete' ? 'completed successfully' : status
      } ${link}"""

    twilioService.sendMessage(
      account,
      from,
      preference.address,
      message
    )
  }

  @Override
  String getNotificationType() {
    'sms'
  }

}
