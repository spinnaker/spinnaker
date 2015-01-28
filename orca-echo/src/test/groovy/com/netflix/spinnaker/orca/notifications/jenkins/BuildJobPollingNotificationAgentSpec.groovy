/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.notifications.jenkins

import com.netflix.spinnaker.orca.echo.EchoEventPoller
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import net.greghaines.jesque.client.Client
import retrofit.MockHttpException
import retrofit.client.Response
import retrofit.mime.TypedString
import rx.schedulers.Schedulers
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static java.util.concurrent.TimeUnit.SECONDS

class BuildJobPollingNotificationAgentSpec extends Specification {

  def mapper = new OrcaObjectMapper()
  def echoEventPoller = Stub(EchoEventPoller)
  def jesqueClient = Mock(Client)
  def scheduler = Schedulers.test()

  @Subject notificationAgent = new BuildJobPollingNotificationAgent(
    mapper,
    echoEventPoller,
    jesqueClient
  )

  @Shared jenkinsEvent = [
    content: [
      project: [
        name     : "SPINNAKER-package-pond",
        lastBuild: [result: "SUCCESS", building: "false"]
      ],
      master : "master1"
    ]
  ]

  def setup() {
    notificationAgent.scheduler = scheduler
  }

  def cleanup() {
    notificationAgent.shutdown()
  }

  def "processes a single event"() {
    given:
    echoEventPoller.getEvents(_) >> echoEventResponse(jenkinsEvent)

    and:
    notificationAgent.init()

    when:
    nextPoll()

    then:
    1 * jesqueClient.enqueue(*_)
  }

  def "processes multiple events"() {
    given:
    echoEventPoller.getEvents(_) >> echoEventResponse(jenkinsEvent, jenkinsEvent)

    and:
    notificationAgent.init()

    when:
    nextPoll()

    then:
    2 * jesqueClient.enqueue(*_)
  }

  def "filters out events that are not from jenkins"() {
    given:
    def event = [content: [:]]
    echoEventPoller.getEvents(_) >> echoEventResponse(event)

    and:
    notificationAgent.init()

    when:
    nextPoll()

    then:
    0 * jesqueClient.enqueue(*_)
  }

  def "recovers from polling errors"() {
    given:
    echoEventPoller.getEvents(_) >> {
      throw MockHttpException.newInternalError(null)
    } >> echoEventResponse(jenkinsEvent)

    and:
    notificationAgent.init()
    nextPoll()

    when:
    nextPoll()

    then:
    1 * jesqueClient.enqueue(*_)
  }

  private nextPoll() {
    scheduler.advanceTimeBy(notificationAgent.pollingInterval, SECONDS)
  }

  private Response echoEventResponse(Map... events) {
    new Response(
      "http://echo", 200, "OK", [],
      new TypedString(mapper.writeValueAsString(events))
    )
  }

}
