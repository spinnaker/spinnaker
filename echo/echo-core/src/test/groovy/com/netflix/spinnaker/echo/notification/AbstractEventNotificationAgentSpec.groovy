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
 *
 */

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.events.Event
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AbstractEventNotificationAgentSpec extends Specification {
  def subclassMock = Mock(AbstractEventNotificationAgent)
  @Subject def agent = new AbstractEventNotificationAgent() {
    @Override
    String getNotificationType() {
      return "fake"
    }

    @Override
    void sendNotifications(Map<String, Object> notification, String application, Event event, Map<String, String> config, String status) {
      subclassMock.sendNotifications(notification, application, event, config, status)
    }
  }

  @Unroll
  def "sends notifications based on status and configuration"() {
    given:
    subclassMock.sendNotifications(*_) >> { notification, application, event_local, config, status -> }

    when:
    agent.processEvent(event)

    then:
    expectedNotifications * subclassMock.sendNotifications(*_)

    where:
    event                                                                                     || expectedNotifications
    // notifications ON, unknown event source
    fakePipelineEvent("whatever:pipeline:complete", "SUCCEEDED", "pipeline.complete")         || 0
    // notifications ON, unknown event sub-type
    fakePipelineEvent("orca:whatever:whatever", "SUCCEEDED", "pipeline.complete")             || 0
    // notifications OFF, succeeded pipeline
    fakePipelineEvent("orca:pipeline:complete", "SUCCEEDED", null)                            || 0
    // notifications OFF, failed pipeline
    fakePipelineEvent("orca:pipeline:failed", "TERMINAL", null)                               || 0
    // notifications ON, succeeded pipeline and matching config
    fakePipelineEvent("orca:pipeline:complete", "SUCCEEDED", "pipeline.complete")             || 1
    // notifications ON, succeeded pipeline and non-matching config
    fakePipelineEvent("orca:pipeline:complete", "SUCCEEDED", "pipeline.failed")               || 0
    // notifications ON, failed pipeline and matching config
    fakePipelineEvent("orca:pipeline:failed", "TERMINAL", "pipeline.failed")                  || 1
    // notifications ON, failed pipeline and non-matching config
    fakePipelineEvent("orca:pipeline:failed", "TERMINAL", "pipeline.complete")                || 0
    // notifications ON, cancelled pipeline (should skip notifications)
    // note: this case is a bit convoluted as the event type is still set to "failed" by
    // orca for cancelled pipelines
    fakePipelineEvent("orca:pipeline:failed", "CANCELED", "pipeline.failed")                  || 0
    // notifications ON, another check for cancelled pipeline (should skip notifications)
    fakePipelineEvent("orca:pipeline:failed", "WHATEVER", "pipeline.failed", [canceled: true]) || 0

    fakeOrchestrationEvent("orca:orchestration:complete", "SUCCEEDED", "orchestration.complete")|| 1
    fakeOrchestrationEvent("orca:orchestration:failed", "TERMINAL", "orchestration.failed")     || 1

    // notifications OFF, stage complete
    fakeStageEvent("orca:stage:complete", null)                                               || 0
    // notifications OFF, stage failed
    fakeStageEvent("orca:stage:complete", null)                                               || 0
    // notifications ON, stage complete, matching config
    fakeStageEvent("orca:stage:complete", "stage.complete")                                   || 1
    // notifications ON, stage complete, non-matching config
    fakeStageEvent("orca:stage:complete", "stage.failed")                                     || 0
    // notifications ON, stage failed, matching config
    fakeStageEvent("orca:stage:failed", "stage.failed")                                       || 1
    // notifications ON, stage failed, non-matching config
    fakeStageEvent("orca:stage:failed", "stage.complete")                                     || 0
    // notifications ON, stage cancelled
    fakeStageEvent("orca:stage:failed", "stage.failed", true)                                 || 0
    // notifications ON, stage is synthetic
    fakeStageEvent("orca:stage:complete", "stage.complete", false, true)                      || 0
  }

  @Unroll
  def "sends notifications for ManualJudgment stage based on status and configuration"() {
    given:
    subclassMock.sendNotifications(*_) >> { notification, application, event_local, config, status -> }

    when:
    agent.processEvent(event)

    then:
    expectedNotifications * subclassMock.sendNotifications(*_)

    where:
    event                                                           || expectedNotifications
    fakeStageEvent("orca:stage:complete", "manualJudgmentContinue") || 1
    fakeStageEvent("orca:stage:starting", "manualJudgment")         || 1
    fakeStageEvent("orca:stage:failed", "manualJudgmentStop")       || 1
  }

  private def fakePipelineEvent(String type, String status, String notifyWhen, Map extraExecutionProps = [:]) {
    def eventProps = [
      details: [type: type],
      content: [
        execution: [
          id: "1",
          name: "foo-pipeline",
          status: status
        ]
      ]
    ]

    if (notifyWhen) {
      eventProps.content.execution << [notifications: [[type: "fake", when: notifyWhen]]]
    }

    eventProps.content.execution << extraExecutionProps

    return new Event(eventProps)
  }

  private def fakeOrchestrationEvent(String type, String status, String notifyWhen, Map extraExecutionProps = [:]) {
    def eventProps = [
      details: [type: type],
      content: [
        execution: [
          id: "1",
          name: "foo-orchestration",
          status: status
        ]
      ]
    ]

    if (notifyWhen) {
      eventProps.content.execution << [notifications: [[type: "fake", when: notifyWhen as String]]]
    }

    eventProps.content.execution << extraExecutionProps

    return new Event(eventProps)
  }

  private def fakeStageEvent(String type, String notifyWhen, canceled = false, synthetic = false) {
    def eventProps = [
      details: [type: type],
      content: [
        canceled: canceled,
        context: [
          stageDetails: [
            isSynthetic: synthetic
          ],
        ]
      ]
    ]

    if (notifyWhen) {
      eventProps.content.context.sendNotifications = true
      eventProps.content.context << [notifications: [[type: "fake", when: notifyWhen as String]]]
    }

    return new Event(eventProps)
  }
}
