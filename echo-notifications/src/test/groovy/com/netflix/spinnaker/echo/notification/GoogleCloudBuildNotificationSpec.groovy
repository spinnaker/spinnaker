/*
 * Copyright 2019 Google, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription
import com.netflix.spinnaker.echo.services.IgorService
import com.netflix.spinnaker.kork.core.RetrySupport
import okhttp3.RequestBody
import spock.lang.Specification
import spock.lang.Subject

class GoogleCloudBuildNotificationSpec extends Specification {
  IgorService igorService = Mock(IgorService)
  RetrySupport retrySupport = new RetrySupport()
  ObjectMapper objectMapper = EchoObjectMapper.getInstance()

  String ACCOUNT_NAME = "my-account"
  String BUILD_ID = "1a9ea355-eb3d-4148-b81b-875d07ea118b"
  String BUILD_STATUS = "QUEUED"
  String PAYLOAD = objectMapper.writeValueAsString([
    "key": "value"
  ])

  @Subject
  GoogleCloudBuildNotificationAgent notificationAgent = new GoogleCloudBuildNotificationAgent(igorService, retrySupport)

  def "ignores non-googleCloudBuild events"() {
    given:
    Event event = createEvent("pubsub")

    when:
    notificationAgent.processEvent(event)

    then:
    0 * igorService._
  }

  def "sends googleCloudBuild events to igor"() {
    given:
    Event event = createEvent("googleCloudBuild")

    when:
    notificationAgent.processEvent(event)

    then:
    1 * igorService.updateBuildStatus(ACCOUNT_NAME, BUILD_ID, BUILD_STATUS, { it -> itToString(it) == PAYLOAD })
    0 * igorService._
  }

  def "retries on failure to communicate with igor"() {
    given:
    Event event = createEvent("googleCloudBuild")

    when:
    notificationAgent.processEvent(event)

    then:
    2 * igorService.updateBuildStatus(ACCOUNT_NAME, BUILD_ID, BUILD_STATUS, { it -> itToString(it) == PAYLOAD }) >>
      { throw new RuntimeException() } >> { }
    0 * igorService._
  }

  private Event createEvent(String type) {
    MessageDescription messageDescription = MessageDescription.builder()
      .subscriptionName(ACCOUNT_NAME)
      .messagePayload(PAYLOAD)
      .messageAttributes([
          buildId: BUILD_ID,
          status: BUILD_STATUS
      ]).build()

    Map<String, Object> content = new HashMap<>()
    content.put("messageDescription", messageDescription)

    Metadata details = new Metadata()
    details.setType(type)

    Event event = new Event()
    event.setContent(content)
    event.setDetails(details)
    return event
  }

  private static String itToString(RequestBody body) {
    def buffer = new okio.Buffer()
    body.writeTo(buffer)
    return buffer.readUtf8()
  }
}
