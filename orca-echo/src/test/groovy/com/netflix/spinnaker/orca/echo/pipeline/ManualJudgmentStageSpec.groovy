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

package com.netflix.spinnaker.orca.echo.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.echo.pipeline.ManualJudgmentStage.*

class ManualJudgmentStageSpec extends Specification {
  @Unroll
  void "should return execution status based on judgmentStatus"() {
    given:
    def task = new WaitForManualJudgmentTask()

    when:
    def result = task.execute(new Stage<>(new Pipeline("orca"), "", context))

    then:
    result.status == expectedStatus
    result.context.isEmpty()

    where:
    context                      || expectedStatus
    [:]                          || ExecutionStatus.RUNNING
    [judgmentStatus: "continue"] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "Continue"] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "stop"]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "STOP"]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "unknown"]  || ExecutionStatus.RUNNING
  }

  void "should only send notifications for supported types"() {
    given:
    def task = new WaitForManualJudgmentTask(echoService: Mock(EchoService))

    when:
    def result = task.execute(new Stage<>(new Pipeline("orca"), "", [notifications: [
      new Notification(type: "email", address: "test@netflix.com"),
      new Notification(type: "hipchat", address: "Hipchat Channel"),
      new Notification(type: "sms", address: "11122223333"),
      new Notification(type: "unknown", address: "unknown")
    ]]))

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.notifications.findAll {
      it.lastNotifiedByNotificationState[NotificationState.manualJudgment]
    }*.type == ["email", "hipchat", "sms"]
  }

  @Unroll
  void "if deprecated notification configuration is in use, only send notifications for awaiting judgment state"() {
    given:
    def task = new WaitForManualJudgmentTask(echoService: Mock(EchoService))

    when:
    def result = task.execute(new Stage<>(new Pipeline("orca"), "", [
      sendNotifications: sendNotifications,
      notifications: [
        new Notification(type: "email", address: "test@netflix.com", when: [ notificationState ])
      ],
      judgmentStatus: judgmentStatus
    ]))

    then:
    result.status == executionStatus
    if (sent) result.context.notifications?.getAt(0)?.lastNotifiedByNotificationState?.containsKey(notificationState)

    where:
    sendNotifications | notificationState                        | judgmentStatus | executionStatus           || sent
    true              | NotificationState.manualJudgment         | null           | ExecutionStatus.RUNNING   || true
    false             | NotificationState.manualJudgment         | null           | ExecutionStatus.RUNNING   || true
    true              | NotificationState.manualJudgmentContinue | "continue"     | ExecutionStatus.SUCCEEDED || true
    false             | NotificationState.manualJudgmentContinue | "continue"     | ExecutionStatus.SUCCEEDED || false
    true              | NotificationState.manualJudgmentStop     | "stop"         | ExecutionStatus.TERMINAL  || true
    false             | NotificationState.manualJudgmentStop     | "stop"         | ExecutionStatus.TERMINAL  || false
  }

  @Unroll
  void "should notify if `notifyEveryMs` duration has been exceeded for the specified notification state"() {
    expect:
    notification.shouldNotify(notificationState, now) == shouldNotify

    where:
    notification                                                                                  | notificationState                        | now             || shouldNotify
    new Notification()                                                                            | NotificationState.manualJudgment         | new Date()      || true
    new Notification(
      lastNotifiedByNotificationState: [(NotificationState.manualJudgment): new Date(1)])         | NotificationState.manualJudgment         | new Date()      || false
    new Notification(
      lastNotifiedByNotificationState: [(NotificationState.manualJudgment): new Date(1)])         | NotificationState.manualJudgmentContinue | new Date()      || true
    new Notification(
      lastNotifiedByNotificationState: [(NotificationState.manualJudgment): new Date(1),
                                        (NotificationState.manualJudgmentContinue): new Date(1)]) | NotificationState.manualJudgmentContinue | new Date()      || false
    new Notification(
      lastNotifiedByNotificationState: [(NotificationState.manualJudgment): new Date(1)],
      notifyEveryMs: 60000)                                                                       | NotificationState.manualJudgment         | new Date(60001) || true
  }

  @Unroll
  void "should update `lastNotified` whenever a notification is sent"() {
    given:
    def echoService = Mock(EchoService)
    def notification = new Notification(type: "sms", address: "111-222-3333")

    def stage = new Stage<>(new Pipeline("orca"), "")
    stage.execution.id = "ID"
    stage.execution.application = "APPLICATION"

    when:
    notification.notify(echoService, stage, notificationState)

    then:
    notification.lastNotifiedByNotificationState[notificationState] != null

    1 * echoService.create({ EchoService.Notification n ->
      assert n.notificationType == EchoService.Notification.Type.SMS
      assert n.to == ["111-222-3333"]
      assert n.templateGroup == notificationState.toString()
      assert n.severity == EchoService.Notification.Severity.HIGH

      assert n.source.executionId == "ID"
      assert n.source.executionType == "pipeline"
      assert n.source.application == "APPLICATION"
      true
    } as EchoService.Notification)
    0 * _

    where:
    notificationState << [
                           NotificationState.manualJudgment,
                           NotificationState.manualJudgmentContinue,
                           NotificationState.manualJudgmentStop
                         ]
  }

  @Unroll
  void "should return modified authentication context"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), "", [
      judgmentStatus                : judgmentStatus,
      propagateAuthenticationContext: propagateAuthenticationContext
    ])
    stage.lastModified = new Stage.LastModifiedDetails(user: "modifiedUser", allowedAccounts: ["group1"])

    when:
    def authenticatedUser = new ManualJudgmentStage().authenticatedUser(stage)

    then:
    authenticatedUser.isPresent() == isPresent
    !isPresent || (authenticatedUser.get().username == "modifiedUser" && authenticatedUser.get().allowedAccounts == ["group1"])

    where:
    judgmentStatus | propagateAuthenticationContext || isPresent
    "continue"     | true                           || true
    "ContinuE"     | true                           || true
    "continue"     | false                          || false
    "stop"         | true                           || false
    "stop"         | false                          || false
    ""             | true                           || false
    null           | true                           || false
  }
}
