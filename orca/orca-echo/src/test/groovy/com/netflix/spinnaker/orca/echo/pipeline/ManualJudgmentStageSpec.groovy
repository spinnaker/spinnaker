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

import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.echo.util.ManualJudgmentAuthorization
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.echo.pipeline.ManualJudgmentStage.Notification
import static com.netflix.spinnaker.orca.echo.pipeline.ManualJudgmentStage.WaitForManualJudgmentTask

class ManualJudgmentStageSpec extends Specification {
  EchoService echoService = Mock(EchoService)

  FiatPermissionEvaluator fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)

  FiatStatus fiatStatus = Mock() {
    _ * isEnabled() >> true
  }

  ManualJudgmentAuthorization manualJudgmentAuthorization = new ManualJudgmentAuthorization(
      Optional.of(fiatPermissionEvaluator),
      fiatStatus
  )

  @Unroll
  void "should return execution status based on judgmentStatus"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), manualJudgmentAuthorization)

    when:
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", context))

    then:
    result.status == expectedStatus

    where:
    context                      || expectedStatus
    [:]                          || ExecutionStatus.RUNNING
    [judgmentStatus: "continue"] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "Continue"] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "stop"]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "STOP"]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "unknown"]  || ExecutionStatus.RUNNING
  }

  @Unroll
  void "should return execution status based on authorizedGroups"() {
    given:
    1 * fiatPermissionEvaluator.getPermission('abc@somedomain.io') >> {
      new UserPermission().addResources([new Role('foo')]).setAdmin(isAdmin).view
    }

    def task = new WaitForManualJudgmentTask(Optional.of(echoService), manualJudgmentAuthorization)

    when:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", context)
    stage.lastModified = new StageExecution.LastModifiedDetails(user: "abc@somedomain.io", allowedAccounts: ["group1"])
    def result = task.execute(stage)

    then:
    result.status == expectedStatus

    where:
    isAdmin | context                                                   || expectedStatus
    false   | [judgmentStatus: "continue", selectedStageRoles: ['foo']] || ExecutionStatus.SUCCEEDED
    false   | [judgmentStatus: "Continue", selectedStageRoles: ['foo']] || ExecutionStatus.SUCCEEDED
    false   | [judgmentStatus: "stop", selectedStageRoles: ['foo']]     || ExecutionStatus.TERMINAL
    false   | [judgmentStatus: "STOP", selectedStageRoles: ['foo']]     || ExecutionStatus.TERMINAL
    false   | [judgmentStatus: "Continue", selectedStageRoles: ['baz']] || ExecutionStatus.RUNNING
    false   | [judgmentStatus: "Stop", selectedStageRoles: ['baz']]     || ExecutionStatus.RUNNING
    true    | [judgmentStatus: "Stop", selectedStageRoles: ['baz']]     || ExecutionStatus.TERMINAL
    true    | [judgmentStatus: "Continue", selectedStageRoles: ['baz']] || ExecutionStatus.SUCCEEDED
  }

  void "should only send notifications for supported types"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), manualJudgmentAuthorization)

    when:
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [notifications: [
      new Notification(type: "email", address: "test@netflix.com"),
      new Notification(type: "hipchat", address: "Hipchat Channel"),
      new Notification(type: "sms", address: "11122223333"),
      new Notification(type: "unknown", address: "unknown"),
      new Notification(type: "pubsub", publisherName: "foobar")
    ]]))

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.notifications.findAll {
      it.lastNotifiedByNotificationState["manualJudgment"]
    }*.type == ["email", "hipchat", "sms", "pubsub"]
  }

  @Unroll
  void "if deprecated notification configuration is in use, only send notifications for awaiting judgment state"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), manualJudgmentAuthorization)

    when:
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
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
    sendNotifications | notificationState        | judgmentStatus | executionStatus           || sent
    true              | "manualJudgment"         | null           | ExecutionStatus.RUNNING   || true
    false             | "manualJudgment"         | null           | ExecutionStatus.RUNNING   || true
    true              | "manualJudgmentContinue" | "continue"     | ExecutionStatus.SUCCEEDED || true
    false             | "manualJudgmentContinue" | "continue"     | ExecutionStatus.SUCCEEDED || false
    true              | "manualJudgmentStop"     | "stop"         | ExecutionStatus.TERMINAL  || true
    false             | "manualJudgmentStop"     | "stop"         | ExecutionStatus.TERMINAL  || false
  }

  @Unroll
  void "should notify if `notifyEveryMs` duration has been exceeded for the specified notification state"() {
    expect:
    notification.shouldNotify(notificationState, now) == shouldNotify

    where:
    notification                                                                  | notificationState        | now             || shouldNotify
    new Notification()                                                            | "manualJudgment"         | new Date()      || true
    new Notification(
      lastNotifiedByNotificationState: [("manualJudgment"): new Date(1)])         | "manualJudgment"         | new Date()      || false
    new Notification(
      lastNotifiedByNotificationState: [("manualJudgment"): new Date(1)])         | "manualJudgmentContinue" | new Date()      || true
    new Notification(
      lastNotifiedByNotificationState: [("manualJudgment"): new Date(1),
                                        ("manualJudgmentContinue"): new Date(1)]) | "manualJudgmentContinue" | new Date()      || false
    new Notification(
      lastNotifiedByNotificationState: [("manualJudgment"): new Date(1)],
      notifyEveryMs: 60000)                                                       | "manualJudgment"         | new Date(60001) || true
  }

  @Unroll
  void "should update `lastNotified` whenever a notification is sent"() {
    given:
    def echoService = Mock(EchoService)
    def notification = new Notification(type: "sms", address: "111-222-3333")

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "")
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
    notificationState << [ "manualJudgment", "manualJudgmentContinue", "manualJudgmentStop" ]
  }

  @Unroll
  void "should retain unknown fields in the notification context"() {
    given:
    def task = new WaitForManualJudgmentTask(Optional.of(echoService), manualJudgmentAuthorization)

    def slackNotification = new Notification(type: "slack")
    slackNotification.setOther("customMessage", "hello slack")

    def emailNotification = new Notification(type: "email")
    emailNotification.setOther("customSubject", "hello email")

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        notifications: [
            slackNotification,
            emailNotification
        ]
    ])

    when:
    def result = task.execute(stage)

    then:
    result.context.notifications?.get(0)?.other?.customMessage == "hello slack"
    result.context.notifications?.get(1)?.other?.customSubject == "hello email"
  }

  @Unroll
  void "should return modified authentication context"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      judgmentStatus                : judgmentStatus,
      propagateAuthenticationContext: propagateAuthenticationContext
    ])
    stage.lastModified = new StageExecution.LastModifiedDetails(user: "modifiedUser", allowedAccounts: ["group1"])

    when:
    def authenticatedUser = new ManualJudgmentStage().authenticatedUser(stage)

    then:
    authenticatedUser.isPresent() == isPresent
    !isPresent || (authenticatedUser.get().user == "modifiedUser" && authenticatedUser.get().allowedAccounts.toList() == ["group1"])

    where:
    judgmentStatus | propagateAuthenticationContext || isPresent
    "continue"     | true                           || true
    "ContinuE"     | true                           || true
    "continue"     | false                          || true
    "stop"         | true                           || false
    "stop"         | false                          || false
    ""             | true                           || false
    null           | true                           || false
  }
}
