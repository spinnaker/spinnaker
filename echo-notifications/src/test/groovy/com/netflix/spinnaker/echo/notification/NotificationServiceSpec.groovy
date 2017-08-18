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

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.hipchat.HipchatMessage
import com.netflix.spinnaker.echo.hipchat.HipchatNotificationService
import com.netflix.spinnaker.echo.hipchat.HipchatService
import com.netflix.spinnaker.echo.twilio.TwilioNotificationService
import com.netflix.spinnaker.echo.twilio.TwilioService
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties
import spock.lang.Shared
import spock.lang.Specification

class NotificationServiceSpec extends Specification {
  @Shared
  def notificationTemplateEngine

  void setup() {
    def autoconfig = new FreeMarkerAutoConfiguration.FreeMarkerNonWebConfiguration()
    autoconfig.properties = new FreeMarkerProperties()
    def config = autoconfig.freeMarkerConfiguration()
    config.afterPropertiesSet()
    notificationTemplateEngine = new NotificationTemplateEngine(
        configuration: config.object,
        spinnakerUrl: "SPINNAKER_URL"
    )
  }

  void "should send specific hipchat message"() {
    given:
    def hipchatService = Mock(HipchatService)
    def hipchatNotificationService = new HipchatNotificationService(
        token: "token",
        notificationTemplateEngine: notificationTemplateEngine,
        hipchat: hipchatService
    )
    def notification = new Notification(
        notificationType: "HIPCHAT",
        templateGroup: "example",
        to: ["room"],
        source: new Notification.Source(application: "application")
    )

    when:
    hipchatNotificationService.handle(notification)

    then:
    1 * hipchatService.sendMessage("token", "room", { HipchatMessage message ->
      message.message == "specific SPINNAKER_URL application"
    } as HipchatMessage)
    }

  void "should send generic twilio message"() {
    given:
    def twilioService = Mock(TwilioService)
    def twilioNotificationService = new TwilioNotificationService(
        account: "account",
        from: "222-333-4444",
        notificationTemplateEngine: notificationTemplateEngine,
        twilioService: twilioService
    )
    def notification = new Notification(
        notificationType: "SMS",
        templateGroup: "example",
        to: ["111-222-3333"],
        source: new Notification.Source(application: "application")
    )

    when:
    twilioNotificationService.handle(notification)

    then:
    1 * twilioService.sendMessage("account", "222-333-4444", "111-222-3333", "generic SPINNAKER_URL application")
  }
}
