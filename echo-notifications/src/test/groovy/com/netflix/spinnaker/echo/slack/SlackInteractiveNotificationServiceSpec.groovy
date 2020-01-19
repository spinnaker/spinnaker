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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.RequestEntity
import spock.lang.Specification
import spock.lang.Subject

class SlackInteractiveNotificationServiceSpec extends Specification {
  def slackAppService = Mock(SlackAppService)
  def slackHookService = Mock(SlackInteractiveNotificationService.SlackHookService)

  @Subject
  def service = new SlackInteractiveNotificationService (
    slackAppService,
    slackHookService,
    Mock(NotificationTemplateEngine),
    new ObjectMapper()
  )

  def "supports the SLACK notification type"() {
    when:
    boolean result = service.supportsType(Notification.Type.SLACK)

    then:
    result == true
  }

  def "parsing an interaction callback from Slack returns the matching generic representation"() {
    given:
    String slackRequestBody = "payload=" +
      URLEncoder.encode(getClass().getResource("/slack/callbackRequestBody.txt").text)

    RequestEntity<String> request = new RequestEntity<>(
      slackRequestBody, new HttpHeaders(), HttpMethod.POST, new URI("/notifications/callbaks"))

    slackAppService.verifyToken(*_) >> { }
    slackAppService.getUserInfo(*_) >> new SlackService.SlackUserInfo(email: "john@doe.com")

    Notification.InteractiveActionCallback expectedCallbackObject = new Notification.InteractiveActionCallback(
      actionPerformed: new Notification.ButtonAction(
        name: "approval",
        value: "yes"
      ),
      serviceId: "keel",
      messageId: "01DYNYK94DE4901K9P802DB4ZT",
      user: "john@doe.com"
    )

    when:
    Notification.InteractiveActionCallback callbackObject = service.parseInteractionCallback(request)

    then:
    callbackObject == expectedCallbackObject
  }

  def "failing to retrieve user info falls back to user name when parsing an interaction callback from Slack"() {
    given:
    String slackRequestBody = "payload=" +
      URLEncoder.encode(getClass().getResource("/slack/callbackRequestBody.txt").text)

    RequestEntity<String> request = new RequestEntity<>(
      slackRequestBody, new HttpHeaders(), HttpMethod.POST, new URI("/notifications/callbaks"))

    slackAppService.verifyToken(*_) >> { }
    slackAppService.getUserInfo(*_) >> { throw new Exception("oops!") }

    Notification.InteractiveActionCallback expectedCallbackObject = new Notification.InteractiveActionCallback(
      actionPerformed: new Notification.ButtonAction(
        name: "approval",
        value: "yes"
      ),
      serviceId: "keel",
      messageId: "01DYNYK94DE4901K9P802DB4ZT",
      user: "john.doe"
    )

    when:
    Notification.InteractiveActionCallback callbackObject = service.parseInteractionCallback(request)

    then:
    callbackObject == expectedCallbackObject
  }

  def "parsing a malformed interaction callback from Slack throws an exception"() {
    given:
    String slackRequestBody = "content=suspicious"
    RequestEntity<String> request = new RequestEntity<>(
      slackRequestBody, new HttpHeaders(), HttpMethod.POST, new URI("/notifications/callbaks"))

    when:
    service.parseInteractionCallback(request)

    then:
    thrown(InvalidRequestException)
  }

  def "failing to verify the token from Slack throws an exception"() {
    given:
    String slackRequestBody = "payload=" +
      URLEncoder.encode(getClass().getResource("/slack/callbackRequestBody.txt").text)

    RequestEntity<String> request = new RequestEntity<>(
      slackRequestBody, new HttpHeaders(), HttpMethod.POST, new URI("/notifications/callbaks"))

    slackAppService.verifyToken(*_) >> { throw new InvalidRequestException() }
    slackAppService.getUserInfo(*_) >> { }

    when:
    service.parseInteractionCallback(request)

    then:
    thrown(InvalidRequestException)
  }

  def "responding to a callback calls Slack with the response_url and replaces the buttons in the original message with the clicked action"() {
    given:
    String payload = getClass().getResource("/slack/callbackRequestBody.txt").text
    String slackRequestBody = "payload=" + URLEncoder.encode(payload, "UTF-8")

    RequestEntity<String> request = new RequestEntity<>(
      slackRequestBody, new HttpHeaders(), HttpMethod.POST, new URI("/notifications/callbaks"))

    slackAppService.verifyToken(*_) >> { }
    slackAppService.getUserInfo(*_) >> { }

    Map parsedPayload = new ObjectMapper().readValue(payload, Map)
    Map originalMessage = parsedPayload.original_message
    Map expectedResponse = new HashMap(originalMessage)
    expectedResponse.attachments[0].remove("actions")
    expectedResponse.attachments[0].text += "\n\nUser <@${parsedPayload.user.id}> clicked the *Approve* action."

    when:
    service.respondToCallback(request)

    then:
    1 * slackHookService.respondToMessage("/actions/T00000000/0123456789/abcdefgh1234567", expectedResponse)
  }
}
