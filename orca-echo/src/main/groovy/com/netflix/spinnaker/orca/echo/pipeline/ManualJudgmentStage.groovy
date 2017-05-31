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

import java.util.concurrent.TimeUnit
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.*
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ManualJudgmentStage implements StageDefinitionBuilder, RestartableStage, AuthenticatedStage {

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder
      .withTask("waitForJudgment", WaitForManualJudgmentTask.class)
  }

  @Override
  void prepareStageForRestart(Stage stage) {
    stage.context.remove("judgmentStatus")
    stage.context.remove("lastModifiedBy")
  }

  @Override
  Optional<User> authenticatedUser(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (stageData.state != StageData.State.CONTINUE || !stage.lastModified?.user || !stageData.propagateAuthenticationContext) {
      return Optional.empty()
    }

    def user = new User()
    user.setAllowedAccounts(stage.lastModified.allowedAccounts)
    user.setUsername(stage.lastModified.user)
    user.setEmail(stage.lastModified.user)
    return Optional.of(user.asImmutable())
  }

  @Slf4j
  @Component
  @VisibleForTesting
  public static class WaitForManualJudgmentTask implements RetryableTask {
    long backoffPeriod = 15000
    long timeout = TimeUnit.DAYS.toMillis(3)

    @Autowired(required = false)
    EchoService echoService

    @Override
    TaskResult execute(Stage stage) {
      StageData stageData = stage.mapTo(StageData)
      NotificationState notificationState
      ExecutionStatus executionStatus

      switch (stageData.state) {
        case StageData.State.CONTINUE:
          notificationState = NotificationState.manualJudgmentContinue
          executionStatus = ExecutionStatus.SUCCEEDED
          break
        case StageData.State.STOP:
          notificationState = NotificationState.manualJudgmentStop
          executionStatus = ExecutionStatus.TERMINAL
          break
        default:
          notificationState = NotificationState.manualJudgment
          executionStatus = ExecutionStatus.RUNNING
          break
      }

      Map outputs = processNotifications(stage, stageData, notificationState)

      return new TaskResult(executionStatus, outputs)
    }

    Map processNotifications(Stage stage, StageData stageData, NotificationState notificationState) {
      if (echoService) {
        // sendNotifications will be true if using the new scheme for configuration notifications.
        // The new scheme matches the scheme used by the other stages.
        // If the deprecated scheme is in use, only the original 'awaiting judgment' notification is supported.
        if (notificationState != NotificationState.manualJudgment && !stage.context.sendNotifications) {
          return [:]
        }

        stageData.notifications.findAll { it.shouldNotify(notificationState) }.each {
          try {
            it.notify(echoService, stage, notificationState)
          } catch (Exception e) {
            log.error("Unable to send notification (executionId: ${stage.execution.id}, address: ${it.address}, type: ${it.type})", e)
          }
        }

        return [notifications: stageData.notifications]
      } else {
        return [:]
      }
    }
  }

  static enum NotificationState {
    manualJudgment,
    manualJudgmentContinue,
    manualJudgmentStop
  }

  static class StageData {
    String judgmentStatus = ""
    List<Notification> notifications = []
    boolean propagateAuthenticationContext

    State getState() {
      switch (judgmentStatus?.toLowerCase()) {
        case "continue":
          return State.CONTINUE
        case "stop":
          return State.STOP
        default:
          return State.UNKNOWN
      }
    }

    enum State {
      CONTINUE,
      STOP,
      UNKNOWN
    }
  }

  static class Notification {
    String address
    String cc
    String type
    List<NotificationState> when
    Map<NotificationState, Map> message

    Map<NotificationState, Date> lastNotifiedByNotificationState = [:]
    Long notifyEveryMs = -1

    boolean shouldNotify(NotificationState notificationState, Date now = new Date()) {
      // The new scheme for configuring notifications requires the use of the when list (just like the other stages).
      // If this list is present, but does not contain an entry for this particular notification state, do not notify.
      if (when && !when.contains(notificationState)) {
        return false
      }

      Date lastNotified = lastNotifiedByNotificationState[notificationState]

      if (!lastNotified?.time) {
        return true
      }

      if (notifyEveryMs <= 0) {
        return false
      }

      return new Date(lastNotified.time + notifyEveryMs) <= now
    }

    void notify(EchoService echoService, Stage stage, NotificationState notificationState) {
      echoService.create(new EchoService.Notification(
        notificationType: EchoService.Notification.Type.valueOf(type.toUpperCase()),
        to: address ? [address] : null,
        cc: cc ? [cc] : null,
        templateGroup: notificationState,
        severity: EchoService.Notification.Severity.HIGH,
        source: new EchoService.Notification.Source(
          executionType: stage.execution.class.simpleName.toLowerCase(),
          executionId: stage.execution.id,
          application: stage.execution.application
        ),
        additionalContext: [
          stageName: stage.name,
          stageId: stage.refId,
          restrictExecutionDuringTimeWindow: stage.context.restrictExecutionDuringTimeWindow,
          execution: stage.execution,
          instructions: stage.context.instructions ?: "",
          message: message?.get(notificationState)?.text,
          judgmentInputs: stage.context.judgmentInputs,
          judgmentInput: stage.context.judgmentInput,
          judgedBy: stage.context.lastModifiedBy
        ]
      ))
      lastNotifiedByNotificationState[notificationState] = new Date()
    }
  }
}
