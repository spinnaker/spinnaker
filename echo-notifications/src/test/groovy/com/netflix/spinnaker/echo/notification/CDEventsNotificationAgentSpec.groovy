/*
    Copyright (C) 2023 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.cdevents.CDEventsBuilderService
import com.netflix.spinnaker.echo.cdevents.CDEventsSenderService
import io.cloudevents.CloudEvent
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable

class CDEventsNotificationAgentSpec extends Specification {

  def cdEventsBuilder = new CDEventsBuilderService();

  def cdeventsSender = Mock(CDEventsSenderService)
  @Subject
  def agent = new CDEventsNotificationAgent(cdEventsSenderService: cdeventsSender, cdEventsBuilderService: cdEventsBuilder, spinnakerUrl: 'http://spinnaker')

  @Unroll
  def "sends CDEvent of type #cdEventsType when pipeline status has #status"() {
    given:
    def cdevent = new BlockingVariable<CloudEvent>()
    cdeventsSender.sendCDEvent(*_) >> { ceToSend, eventsBrokerURL ->
      cdevent.set(ceToSend)
    }

    when:
    agent.sendNotifications([address: brokerURL, cdEventsType: cdEventsType], application, event, [type: type, link: "link"], status)

    then:
    cdevent.get().getType() ==~ expectedType

    where:
    cdEventsType      || expectedType || status
    "dev.cdevents.pipelinerun.queued" || /dev.cdevents.pipelinerun.queued.0.1.0/ || /starting/
    "dev.cdevents.pipelinerun.started" || /dev.cdevents.pipelinerun.started.0.1.0/ || /started/
    "dev.cdevents.pipelinerun.finished" || /dev.cdevents.pipelinerun.finished.0.1.0/ || /complete/
    "dev.cdevents.taskrun.started" || /dev.cdevents.taskrun.started.0.1.0/ || /started/
    "dev.cdevents.taskrun.finished" || /dev.cdevents.taskrun.finished.0.1.0/ || /complete/


    brokerURL = "http://dev.cdevents.server/default/events-broker"
    application = "whatever"
    event = new Event(content: [
      execution: [id: "1", name: "foo-pipeline"]
    ])
    type = "pipeline"
  }
}
