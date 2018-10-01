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



package com.netflix.spinnaker.echo.email

import com.netflix.spinnaker.echo.controller.EchoResponse
import com.netflix.spinnaker.echo.notification.NotificationService
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

import javax.mail.internet.MimeMessage

/**
 * Mail Sending Service
 */
@Component
@ConditionalOnProperty('mail.enabled')
class EmailNotificationService implements NotificationService {
  private static Notification.Type TYPE = Notification.Type.EMAIL

  @Autowired
  JavaMailSender javaMailSender

  @Autowired
  NotificationTemplateEngine notificationTemplateEngine

  @Value('${mail.from}')
  String from

  @Override
  boolean supportsType(Notification.Type type) {
    return type == TYPE
  }

  @Override
  EchoResponse.Void handle(Notification notification) {
    def to = notification.to?.collect { it.split(" ") }?.flatten() as String[]
    def cc = notification.cc?.collect { it.split(" ") }?.flatten() as String[]
    def subject = notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.SUBJECT)
    def body = notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.BODY)

    send(to, cc, subject, body)
    new EchoResponse.Void()
  }

  void send(String[] to, String[] cc, String subject, String text) {
    MimeMessage message = javaMailSender.createMimeMessage()
    MimeMessageHelper helper = new MimeMessageHelper(message, false, "utf-8")

    if (to) {
      helper.setTo(to)
    }

    if (cc) {
      helper.setCc(cc)
    }

    if (to || cc) {
      helper.setFrom(from)
      message.setContent(text, "text/html;charset=UTF-8")
      helper.setSubject(subject)

      javaMailSender.send(message)
    }
  }
}
