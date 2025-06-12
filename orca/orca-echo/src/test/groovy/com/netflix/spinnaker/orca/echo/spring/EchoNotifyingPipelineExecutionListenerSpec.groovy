/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.echo.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.ApplicationNotifications
import com.netflix.spinnaker.orca.front50.model.ApplicationNotifications.Notification
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.slf4j.MDC
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE

class EchoNotifyingPipelineExecutionListenerSpec extends Specification {

  def echoService = Mock(EchoService)
  def front50Service = Mock(Front50Service)
  def objectMapper = new ObjectMapper()

  @Shared ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()

  @Subject
  def echoListener = new EchoNotifyingExecutionListener(echoService, front50Service, objectMapper, contextParameterProcessor)

  @Shared
  ApplicationNotifications notifications = new ApplicationNotifications()

  ApplicationNotifications notificationsWithSpel = new ApplicationNotifications()

  @Shared
  Notification slackPipes

  @Shared
  Notification slackTasks

  @Shared
  Notification emailTasks

  void setup() {
    slackPipes = new Notification([
      when   : ["pipeline.started", "pipeline.failed"],
      type   : "slack",
      address: "spinnaker"
    ])
    slackTasks = new Notification([
      when   : ["task.completed"],
      type   : "slack",
      address: "spinnaker-tasks"
    ])
    emailTasks = new Notification([
      when: ["task.started"],
      type: "email"
    ])

    notifications.set("slack", [slackPipes, slackTasks])
    notifications.set("email", [emailTasks])

    def slackPipesWithSpel = new Notification([
      when   : ["pipeline.started", "pipeline.failed"],
      type   : "slack",
      address: '${"spinnaker"}'
    ])
    notificationsWithSpel.set("slack", [slackPipesWithSpel, slackTasks])
    notificationsWithSpel.set("email", [emailTasks])
  }

  void "sends events with expected type to Echo"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("myapp")
    front50Service.getApplicationNotifications("myapp") >> { return Calls.response(notifications)}

    when:
    echoListener.beforeExecution(null, pipeline)

    then:
    1 * echoService.recordEvent({ it.details.type == "orca:pipeline:starting"}) >> Calls.response(null)

    when:
    echoListener.afterExecution(null, pipeline, ExecutionStatus.SUCCEEDED, true)

    then:
    1 * echoService.recordEvent({ it.details.type == "orca:pipeline:complete"}) >> Calls.response(null)
  }

  void "adds notifications to pipeline on beforeExecution"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("myapp")

    when:
    echoListener.beforeExecution(null, pipeline)

    then:
    pipeline.notifications == [slackPipes]
    1 * front50Service.getApplicationNotifications("myapp") >> Calls.response(notifications)
    1 * echoService.recordEvent(_) >> Calls.response(null)
    0 * _
  }

  void "adds notifications to pipeline on afterExecution"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("myapp")

    when:
    echoListener.afterExecution(null, pipeline, ExecutionStatus.TERMINAL, false)

    then:
    pipeline.notifications == [slackPipes]
    1 * front50Service.getApplicationNotifications("myapp") >> Calls.response(notifications)
    1 * echoService.recordEvent(_) >> Calls.response(null)
    0 * _
  }

  void "dedupes notifications"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("myapp")
    def pipelineConfiguredNotification = [
      when   : ["pipeline.started", "pipeline.completed"],
      type   : "slack",
      address: "spinnaker",
      extraField: "extra"
    ]
    pipeline.notifications.add(pipelineConfiguredNotification)

    when:
    echoListener.beforeExecution(null, pipeline)

    then:
    pipeline.notifications.size() == 2
    pipeline.notifications.when.containsAll(["pipeline.started", "pipeline.completed"], ["pipeline.failed"])
    pipeline.notifications.extraField.containsAll("extra", null)
    1 * front50Service.getApplicationNotifications("myapp") >> Calls.response(notifications)
    1 * echoService.recordEvent(_) >> Calls.response(null)
    0 * _
  }

  void "evaluates SpEL and correctly dedupes notifications"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("myapp")
    def pipelineConfiguredNotification = [
      when   : ["pipeline.started", "pipeline.completed"],
      type   : "slack",
      address: '${"spin" + "naker"}',
      extraField: "extra"
    ]
    pipeline.notifications.add(pipelineConfiguredNotification)

    when:
    echoListener.beforeExecution(null, pipeline)

    then:
    pipeline.notifications.size() == 2
    pipeline.notifications.when.containsAll(["pipeline.started", "pipeline.completed"], ["pipeline.failed"])
    pipeline.notifications.extraField.containsAll("extra", null)
    1 * front50Service.getApplicationNotifications("myapp") >> Calls.response(notificationsWithSpel)
    1 * echoService.recordEvent(_) >> Calls.response(null)
    0 * _
  }

  void "handles case where no notifications are present"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("myapp")

    when:
    echoListener.beforeExecution(null, pipeline)

    then:
    pipeline.notifications == []
    1 * front50Service.getApplicationNotifications("myapp") >> Calls.response(null)
    1 * echoService.recordEvent(_) >> Calls.response(null)
    0 * _
  }

  void "handles case where no application notifications are present"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("myapp")
    def pipelineConfiguredNotification = [
      when   : ["pipeline.started", "pipeline.completed"],
      type   : "slack",
      address: "spinnaker"
    ]
    pipeline.notifications.add(pipelineConfiguredNotification)

    when:
    echoListener.beforeExecution(null, pipeline)

    then:
    pipeline.notifications.size() == 1
    pipeline.notifications[0].when == ["pipeline.started", "pipeline.completed"]
    1 * front50Service.getApplicationNotifications("myapp") >> Calls.response(null)
    1 * echoService.recordEvent(_) >> Calls.response(null)
    0 * _
  }

  def "propagates authentication details to front50"() {
    given:
    def pipeline = new PipelineExecutionImpl(PIPELINE, "myapp")
    pipeline.setAuthentication(new PipelineExecution.AuthenticationDetails("user@schibsted.com", "someAccount", "anotherAccount"))

    when:
    echoListener.beforeExecution(null, pipeline)

    then:
    pipeline.notifications == [slackPipes]

    1 * front50Service.getApplicationNotifications("myapp") >> {
      assert MDC.get(Header.USER.header) == "user@schibsted.com"
      assert MDC.get(Header.ACCOUNTS.header).split(",").toList().toSorted() == ["anotheraccount", "someaccount"]
      return Calls.response(notifications)
    }
    1 * echoService.recordEvent(_) >> Calls.response(null)
  }

  def "handles cases with multiple notifications of the same type"() {
    given:
    def notification1 = new Notification([
      address: "test-notify-1",
      level  : "application",
      type   : "slack",
      when   : ["pipeline.starting", "pipeline.complete", "pipeline.failed"]
    ])
    def notification2 = new Notification([
      address: "test-notify-2",
      level  : "application",
      type   : "slack",
      when   : ["pipeline.starting", "pipeline.complete", "pipeline.failed"]
    ])
    notifications.set("slack", [notification1, notification2])
    def pipeline = new PipelineExecutionImpl(PIPELINE, "myapp")

    when:
    echoListener.beforeExecution(null, pipeline)

    then:
    pipeline.notifications.size() == 2
    pipeline.notifications.containsAll(notification1, notification2)

    1 * front50Service.getApplicationNotifications("myapp") >> Calls.response(notifications)
    1 * echoService.recordEvent(_) >> Calls.response(null)
  }
}
