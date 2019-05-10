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

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import org.springframework.mail.javamail.JavaMailSenderImpl
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.mail.Message
import javax.mail.internet.MimeMessage

class EmailNotificationServiceSpec extends Specification {
  def notificationTemplateEngine = Mock(NotificationTemplateEngine)

  @Shared
  @Subject
  EmailNotificationService service = new EmailNotificationService()

  @Shared
  GreenMail greenMail

  void setup() {
    greenMail = new GreenMail(ServerSetupTest.SMTP);
    greenMail.start()

    JavaMailSenderImpl sender = new JavaMailSenderImpl()
    sender.setHost('localhost')
    sender.setPort(3025)

    service.javaMailSender = sender
    service.notificationTemplateEngine = notificationTemplateEngine
    service.from = 'me@localhost'
  }

  void cleanup() {
    greenMail.stop()
  }

  @Unroll
  void 'can send an email message correctly'() {
    given:
    String message = 'email body' + GreenMailUtil.random()
    String subject = 'subject' + GreenMailUtil.random()

    def notification = new Notification(
      to: to,
      cc: cc
    )

    when:
    service.handle(notification)

    then:
    1 * notificationTemplateEngine.build(_, _) >> { return subject }
    1 * notificationTemplateEngine.build(_, _) >> { return message }

    greenMail.waitForIncomingEmail(5000, 1)

    when:
    MimeMessage mail = greenMail.getReceivedMessages()[0]

    then:
    mail.subject == subject
    GreenMailUtil.getBody(mail) == message
    GreenMailUtil.getAddressList(mail.from) == service.from
    GreenMailUtil.getAddressList(mail.getRecipients(Message.RecipientType.TO)) == expectedTo?.join(", ")
    GreenMailUtil.getAddressList(mail.getRecipients(Message.RecipientType.CC)) == expectedCC?.join(", ")

    where:
    to                                        | cc                                                 || expectedTo                                  || expectedCC
    ['receiver@localhost']                    | null                                               || ['receiver@localhost']                      || null
    null                                      | ['some-addr@localhost']                            || null                                        || ['some-addr@localhost']
    ['receiver@localhost']                    | ['some-addr@localhost']                            || ['receiver@localhost']                      || ['some-addr@localhost']
    ['receiver@localhost']                    | ['some-addr@localhost']                            || ['receiver@localhost']                      || ['some-addr@localhost']
    ['receiver@localhost another@localhost']  | ['some-addr@localhost some-other-addr@localhost']  || ['receiver@localhost', 'another@localhost'] || ['some-addr@localhost', 'some-other-addr@localhost']
    ['receiver@localhost,another@localhost']  | ['some-addr@localhost,some-other-addr@localhost']  || ['receiver@localhost', 'another@localhost'] || ['some-addr@localhost', 'some-other-addr@localhost']
    ['receiver@localhost; another@localhost'] | ['some-addr@localhost, some-other-addr@localhost'] || ['receiver@localhost', 'another@localhost'] || ['some-addr@localhost', 'some-other-addr@localhost']
  }
}
