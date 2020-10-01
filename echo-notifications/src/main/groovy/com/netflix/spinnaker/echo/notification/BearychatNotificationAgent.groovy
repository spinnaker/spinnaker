/*
 * Copyright 2018 xiaohongshu, Inc.
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

import com.netflix.spinnaker.echo.bearychat.BearychatService
import com.netflix.spinnaker.echo.bearychat.BearychatUserInfo
import com.netflix.spinnaker.echo.bearychat.CreateP2PChannelPara
import com.netflix.spinnaker.echo.bearychat.CreateP2PChannelResponse
import com.netflix.spinnaker.echo.bearychat.SendMessagePara
import com.netflix.spinnaker.echo.api.events.Event
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.text.WordUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import static net.logstash.logback.argument.StructuredArguments.kv

@Slf4j
@ConditionalOnProperty('bearychat.enabled')
@Service
class BearychatNotificationAgent extends AbstractEventNotificationAgent {

  @Autowired
  BearychatService bearychatService

  @Value('${bearychat.token}')
  String token

  @Override
  String getNotificationType() {
    'bearychat'
  }


  @Override
  void sendNotifications(Map<String, Object> preference, String application, Event event, Map<String, String> config, String status) {
    String buildInfo = ''

    if (config.type == 'pipeline' || config.type == 'stage') {
      if (event.content?.execution?.trigger?.buildInfo?.url) {
        buildInfo = """build #<a href="${event.content.execution.trigger.buildInfo.url}">${
          event.content.execution.trigger.buildInfo.number as Integer
        }</a> """
      }
    }
    log.info('Sending bearychat message {} for {} {} {} {}', kv('address', preference.address), kv('application', application), kv('type', config.type), kv('status', status), kv('executionId', event.content?.execution?.id))


    String message = ''

    if (config.type == 'stage') {
      String stageName = event.content.name ?: event.content?.context?.stageDetails?.name
      message = """Stage $stageName for """
    }

    String link = "${spinnakerUrl}/#/applications/${application}/${config.type == 'stage' ? 'executions/details' : config.link }/${event.content?.execution?.id}"

    message +=
      """${WordUtils.capitalize(application)}'s ${
        event.content?.execution?.name ?: event.content?.execution?.description
      } ${buildInfo} ${config.type == 'task' ? 'task' : 'pipeline'} ${status == 'starting' ? 'is' : 'has'} ${
        status == 'complete' ? 'completed successfully' : status
      }.  To see more details, please visit: ${link}"""

    String customMessage = event.content?.context?.customMessage
    if (customMessage) {
      message = customMessage
        .replace("{{executionId}}", (String) event.content.execution?.id ?: "")
        .replace("{{link}}", link ?: "")
    }

    List<BearychatUserInfo> userList = bearychatService.getUserList(token)
    String userid = userList.find {it.email == preference.address}.id
    CreateP2PChannelResponse channelInfo = bearychatService.createp2pchannel(token,new CreateP2PChannelPara(user_id: userid))
    String channelId = channelInfo.vchannel_id
    bearychatService.sendMessage(token,new SendMessagePara(vchannel_id: channelId,
      text: message,
      attachments: "" ))
  }
}
