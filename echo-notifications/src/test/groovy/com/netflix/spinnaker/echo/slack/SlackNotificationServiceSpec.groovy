/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.slack

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.config.SlackLegacyProperties
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import spock.lang.Specification
import spock.lang.Subject

class SlackNotificationServiceSpec extends Specification {

  def slack = Mock(SlackService)
  @Subject def service = new SlackNotificationService (
    slack,
    new NotificationTemplateEngine()
  )

  def "supports the SLACK notification type"() {
    when:
    boolean result = service.supportsType("SLACK")

    then:
    result == true
  }

  def "handling a notification causes a Slack message to be sent to each channel in the to field"() {
    given:
    Notification notification = new Notification()
    notification.notificationType = "SLACK"
    notification.to = [ "channel1", "channel2" ]
    notification.severity = Notification.Severity.NORMAL
    notification.additionalContext["body"] = "text"

    slack.config >> new SlackLegacyProperties()

    slack.sendMessage(*_) >> { message, channel, asUser -> }

    when:
    service.handle(notification)

    then:
    notification.to.size() * slack.sendMessage(*_)
  }
}
