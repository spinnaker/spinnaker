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

import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine.HtmlToPlainTextFormatter
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine.MarkdownToHtmlFormatter
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.echo.email.EmailNotificationService
import com.netflix.spinnaker.echo.model.Event
import freemarker.template.Configuration
import freemarker.template.Template
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson
import static net.logstash.logback.argument.StructuredArguments.*

@Slf4j
@ConditionalOnProperty('mail.enabled')
@Service
class EmailNotificationAgent extends AbstractEventNotificationAgent {

  @Autowired
  EmailNotificationService mailService

  @Autowired
  Configuration configuration

  @Override
  void sendNotifications(Map preference, String application, Event event, Map config, String status) {
    Map context = event.content?.context ?: [:]
    String buildInfo = ''

    if (config.type == 'pipeline' || config.type == 'stage') {
      if (event.content?.execution?.trigger?.buildInfo?.url) {
        buildInfo = """build #${
          event.content.execution.trigger.buildInfo.number as Integer
        } """
      }
    }

    String subject

    if (config.type == 'stage') {
      String stageName = event.content.name ?: context.stageDetails.name
      subject = """Stage $stageName for ${
        application
      }'s ${event.content?.execution?.name} pipeline ${buildInfo}"""
    } else if (config.type == 'pipeline') {
      subject = """${application}'s ${
        event.content?.execution?.name
      } pipeline ${buildInfo}"""
    } else {
      subject = """${application}'s ${event.content?.execution?.id} task """
    }

    subject += """${status == 'starting' ? 'is' : 'has'} ${
      status == 'complete' ? 'completed successfully' : status
    }"""

    subject = preference.customSubject ?: context.customSubject ?: subject

    log.info('Sending email {} for {} {} {} {}', kv('address', preference.address), kv('application', application), kv('type', config.type), kv('status', status), kv('executionId', event.content?.execution?.id))

    String link = "${spinnakerUrl}/#/applications/${application}/${config.type == 'stage' ? 'executions/details' : config.link }/${event.content?.execution?.id}"

    sendMessage(
      preference.address ? [preference.address] as String[] : null,
      preference.cc ? [preference.cc] as String[] : null,
      event,
      subject,
      config.type,
      status,
      link,
      preference.message?."$config.type.$status"?.text,
      preference.customBody ?: context.customBody
    )
  }

  @Override
  String getNotificationType() {
    'email'
  }

  private void sendMessage(String[] email, String[] cc, Event event, String title, String type, String status, String link, String customMessage, String customBody) {
    String body
    if (customBody) {
      String interpolated = customBody
        .replace("{{executionId}}", (String) event.content?.execution?.id ?: "")
        .replace("{{link}}", link ?: "")
      body = new MarkdownToHtmlFormatter().convert(interpolated)
    } else {
      Template template = configuration.getTemplate(type == 'stage' ? 'stage.ftl' : 'pipeline.ftl', "UTF-8")
      body = FreeMarkerTemplateUtils.processTemplateIntoString(
        template,
        [
          event         : prettyPrint(toJson(event.content)),
          url           : spinnakerUrl,
          htmlToText    : new HtmlToPlainTextFormatter(),
          markdownToHtml: new MarkdownToHtmlFormatter(),
          application   : event.details?.application,
          executionId   : event.content?.execution?.id,
          type          : type,
          status        : status,
          link          : link,
          name          : event.content?.execution?.name ?: event.content?.execution?.description,
          message       : customMessage
        ]
      )
    }

    mailService.send(email, cc, title, body)
  }
}
