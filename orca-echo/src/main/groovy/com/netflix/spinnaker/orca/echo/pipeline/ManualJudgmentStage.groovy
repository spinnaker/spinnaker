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

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.orca.AuthenticatedStage
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.echo.util.ManualJudgmentAuthzGroupsUtil
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit

@Component
class ManualJudgmentStage implements StageDefinitionBuilder, AuthenticatedStage {

  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
      .withTask("waitForJudgment", WaitForManualJudgmentTask.class)
  }

  @Override
  void prepareStageForRestart(@Nonnull StageExecution stage) {
    stage.context.remove("judgmentStatus")
    stage.context.remove("lastModifiedBy")
  }

  @Override
  Optional<User> authenticatedUser(StageExecution stage) {
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
  public static class WaitForManualJudgmentTask implements OverridableTimeoutRetryableTask {
    final long backoffPeriod = 15000
    final long timeout = TimeUnit.DAYS.toMillis(3)

    private final EchoService echoService

    private final FiatPermissionEvaluator fiatPermissionEvaluator

    private FiatStatus fiatStatus

    private ManualJudgmentAuthzGroupsUtil manualJudgmentAuthzGroupsUtil

    private ObjectMapper objectMapper

    @Autowired
    WaitForManualJudgmentTask(Optional<EchoService> echoService, Optional<FiatPermissionEvaluator> fpe,
                              Optional<FiatStatus> fiatStatus, Optional<ObjectMapper> objectMapper,
                              Optional<ManualJudgmentAuthzGroupsUtil> manualJudgmentAuthzGroupsUtil) {
      this.echoService = echoService.orElse(null)
      this.fiatPermissionEvaluator = fpe.orElse(null)
      this.fiatStatus = fiatStatus.orElse(null)
      this.objectMapper = objectMapper.orElse(null)
      this.manualJudgmentAuthzGroupsUtil = manualJudgmentAuthzGroupsUtil.orElse(null)
    }

    @Override
    TaskResult execute(StageExecution stage) {
      StageData stageData = stage.mapTo(StageData)
      def username = AuthenticatedRequest.getSpinnakerUser().orElse(stage.lastModified ? stage.lastModified.user : "")
      boolean fiatEnabled = fiatStatus ? fiatStatus.isEnabled() : false
      boolean isAuthorized = false
      def appPermissions
      def stageRoles
      if (fiatEnabled) {
        stageRoles = stage.context.selectedStageRoles
        if (stageRoles) {
          appPermissions = getApplicationPermissions(stage)
        }
      }
      String notificationState
      ExecutionStatus executionStatus

      switch (stageData.state) {
        case StageData.State.CONTINUE:
          isAuthorized = !fiatEnabled || checkManualJudgmentAuthorizedGroups(stageRoles, appPermissions, username)
          notificationState = "manualJudgmentContinue"
          executionStatus = ExecutionStatus.SUCCEEDED
          break
        case StageData.State.STOP:
          isAuthorized = !fiatEnabled || checkManualJudgmentAuthorizedGroups(stageRoles, appPermissions, username)
          notificationState = "manualJudgmentStop"
          executionStatus = ExecutionStatus.TERMINAL
          break
        default:
          notificationState = "manualJudgment"
          executionStatus = ExecutionStatus.RUNNING
          break
      }
      if (!isAuthorized) {
        notificationState = "manualJudgment"
        executionStatus = ExecutionStatus.RUNNING
        stage.context.put("judgmentStatus", "")
      }
      Map outputs = processNotifications(stage, stageData, notificationState)

      return TaskResult.builder(executionStatus).context(outputs).build()
    }

    private Map<String, Object> getApplicationPermissions(StageExecution stage) {

      def applicationName = stage.execution.application
      def permissions
      if (applicationName) {
        manualJudgmentAuthzGroupsUtil.getApplication(applicationName).ifPresent({ application ->
          if (application.getPermission().permissions && application.getPermission().permissions.permissions) {
            permissions = objectMapper.convertValue(application.getPermission().permissions.permissions,
                new TypeReference<Map<String, Object>>() {})
          }
        });
      }
      return permissions
    }

    boolean checkManualJudgmentAuthorizedGroups(List<String> stageRoles, Map<String, Object> permissions, String username) {

      if (!Strings.isNullOrEmpty(username)) {
        UserPermission.View permission = fiatPermissionEvaluator.getPermission(username);
        if (permission == null) { // Should never happen?
          log.warn("Attempted to get user permission for '$username' but none were found.")
          return false;
        }
        // User has to have all the pipeline roles.
        def userRoles = permission.getRoles().collect { it.getName().trim() }
        return ManualJudgmentAuthzGroupsUtil.checkAuthorizedGroups(userRoles, stageRoles, permissions)
      } else {
        return false
      }
    }

    Map processNotifications(StageExecution stage, StageData stageData, String notificationState) {
      if (echoService) {
        // sendNotifications will be true if using the new scheme for configuration notifications.
        // The new scheme matches the scheme used by the other stages.
        // If the deprecated scheme is in use, only the original 'awaiting judgment' notification is supported.
        if (notificationState != "manualJudgment" && !stage.context.sendNotifications) {
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
    String publisherName
    List<String> when
    Map<String, Map> message

    Map<String, Date> lastNotifiedByNotificationState = [:]
    Long notifyEveryMs = -1

    @JsonIgnore
    Map<String, Object> other = new HashMap<>()

    @JsonAnyGetter
    Map<String, Object> other() {
      return other
    }

    @JsonAnySetter
    void setOther(String name, Object value) {
      other.put(name, value)
    }

    boolean shouldNotify(String notificationState, Date now = new Date()) {
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

    void notify(EchoService echoService, StageExecution stage, String notificationState) {
      echoService.create(new EchoService.Notification(
        notificationType: EchoService.Notification.Type.valueOf(type.toUpperCase()),
        to: address ? [address] : (publisherName ? [publisherName] : null),
        cc: cc ? [cc] : null,
        templateGroup: notificationState,
        severity: EchoService.Notification.Severity.HIGH,
        source: new EchoService.Notification.Source(
          executionType: stage.execution.type.toString(),
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
