/*
 * Copyright 2026 Spinnaker contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.EcsNativeService;
import com.netflix.spinnaker.orca.clouddriver.model.EcsServiceDeploymentStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Polls clouddriver for the status of one pinned {@code ecs-native} service deployment until it
 * succeeds (or reaches its lifecycle-pause gate), fails, or is rolled back.
 *
 * <p>Timeout: the base {@link #getTimeout()} of one hour covers the rollout itself. {@link
 * #getDynamicTimeout(StageExecution)} extends it per stage by the deployment's ECS bake time
 * ({@code bakeTimeInMinutes}) and, unless this wait stops at the lifecycle gate, by the lifecycle
 * pause timeout ({@code lifecyclePauseTimeoutMinutes}, only when {@code lifecyclePauseStage} is
 * set). The settings are read from this stage's own context when it carries them (deploy,
 * rollback), otherwise from the deploy stage the ARN was resolved from (Continue, Stop, standalone
 * wait). Because this is an {@link OverridableTimeoutRetryableTask}, a {@code stageTimeoutMs} on
 * the stage still overrides the derived value.
 */
@Slf4j
@Component
public class WaitForEcsNativeServiceDeploymentTask implements OverridableTimeoutRetryableTask {

  public static final String TASK_NAME = "waitForEcsNativeServiceDeployment";
  public static final String EXPECTED_SERVICE_DEPLOYMENT_ARN =
      "ecsNativeExpectedServiceDeploymentArn";
  static final String NOT_FOUND_RETRY_COUNT = "ecsNativeDeploymentStatus.notFoundRetryCount";

  /**
   * Consecutive 404s tolerated before failing. clouddriver answers 404 while ECS has not exposed
   * the new service deployment yet or the service is not in its cache yet; both normally clear
   * within a few caching cycles. 30 polls at the 10s backoff (~5 minutes, the same count
   * MonitorKatoTask allows for a missing kato task) comfortably cover that lag while turning a
   * permanent 404 into a failure instead of an hour-long silent wait.
   */
  static final int MAX_NOT_FOUND_RETRIES = 30;

  private static final long BACKOFF_PERIOD = TimeUnit.SECONDS.toMillis(10);
  private static final long TIMEOUT = TimeUnit.HOURS.toMillis(1);
  private static final String BAKE_TIME_IN_MINUTES = "bakeTimeInMinutes";
  private static final String LIFECYCLE_PAUSE_STAGE = "lifecyclePauseStage";
  private static final String LIFECYCLE_PAUSE_TIMEOUT_MINUTES = "lifecyclePauseTimeoutMinutes";
  private static final String WAIT_FOR_LIFECYCLE_GATE = "ecsNativeWaitForLifecycleGate";
  private static final String WAIT_FOR_STOPPED = "ecsNativeWaitForStopped";
  private static final String ACCEPT_ROLLBACK = "ecsNativeAcceptRollback";
  private static final String LIFECYCLE_GATE_STATUS = "IN_PROGRESS";
  private static final String HOOK_AWAITING_ACTION = "AWAITING_ACTION";
  private static final Set<String> TERMINAL_FAILURE_STATUSES =
      Set.of("ROLLBACK_SUCCESSFUL", "ROLLBACK_FAILED", "STOPPED", "FAILED");

  @Autowired private EcsNativeService ecsNativeService;

  @Override
  public long getBackoffPeriod() {
    return BACKOFF_PERIOD;
  }

  @Override
  public long getTimeout() {
    return TIMEOUT;
  }

  @Override
  public long getDynamicTimeout(StageExecution stage) {
    Map<String, Object> settings = deploymentSettings(stage);
    long timeout = TIMEOUT + TimeUnit.MINUTES.toMillis(minutes(settings.get(BAKE_TIME_IN_MINUTES)));
    // A wait that stops at the lifecycle gate finishes before the pause starts; any other wait on a
    // paused deployment (standalone wait, Continue) may have to sit out the pause timeout.
    boolean stopsAtLifecycleGate =
        Boolean.TRUE.equals(stage.getContext().get(WAIT_FOR_LIFECYCLE_GATE));
    Object pauseStage = settings.get(LIFECYCLE_PAUSE_STAGE);
    if (!stopsAtLifecycleGate && pauseStage instanceof String && !((String) pauseStage).isBlank()) {
      timeout += TimeUnit.MINUTES.toMillis(minutes(settings.get(LIFECYCLE_PAUSE_TIMEOUT_MINUTES)));
    }
    return timeout;
  }

  /**
   * The context holding this deployment's native ECS settings: this stage's own when it started the
   * deployment or carries the settings, otherwise the deploy stage the ARN is resolved from.
   */
  private static Map<String, Object> deploymentSettings(StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    if (context.containsKey(BAKE_TIME_IN_MINUTES)
        || context.containsKey(LIFECYCLE_PAUSE_STAGE)
        || !ownKatoTaskDeployments(context).isEmpty()) {
      return context;
    }
    return findReferencedDeploymentStage(stage).map(StageExecution::getContext).orElse(context);
  }

  /** Parses a non-negative minute count (number or numeric string); anything else counts as 0. */
  private static long minutes(Object value) {
    long minutes = 0;
    if (value instanceof Number) {
      minutes = ((Number) value).longValue();
    } else if (value instanceof String) {
      try {
        minutes = Long.parseLong(((String) value).trim());
      } catch (NumberFormatException e) {
        minutes = 0;
      }
    }
    return Math.max(0, minutes);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    String account = (String) context.get("account");
    if (account == null) {
      account = (String) context.get("credentials");
    }
    String region = resolveRegion(context);
    String serverGroupName = resolveServerGroupName(context, region);
    String expectedServiceDeploymentArn = resolveExpectedServiceDeploymentArn(stage);

    if (account == null || region == null || serverGroupName == null) {
      throw new IllegalArgumentException(
          "waitForEcsNativeServiceDeployment requires account, region, and serverGroupName "
              + "(either directly in the stage context, or via a preceding deploy stage's "
              + "deploy.server.groups output)");
    }
    if (expectedServiceDeploymentArn == null || expectedServiceDeploymentArn.isBlank()) {
      throw new IllegalArgumentException(
          "waitForEcsNativeServiceDeployment requires ecsNativeExpectedServiceDeploymentArn; "
              + "deployment status cannot safely select an unpinned service deployment");
    }

    try {
      EcsServiceDeploymentStatus status =
          Retrofit2SyncCall.execute(
              ecsNativeService.getServiceDeploymentStatus(
                  account, region, serverGroupName, expectedServiceDeploymentArn));

      String serviceDeploymentStatus =
          status.getStatus() != null ? status.getStatus() : status.getRolloutState();
      log.info(
          "ecs-native service deployment {} for {}: status={} lifecycleStage={} reason={}",
          status.getServiceDeploymentArn(),
          serverGroupName,
          serviceDeploymentStatus,
          status.getLifecycleStage(),
          status.getStatusReason() != null
              ? status.getStatusReason()
              : status.getRolloutStateReason());

      if (!expectedServiceDeploymentArn.equals(status.getServiceDeploymentArn())) {
        log.warn(
            "ecs-native service deployment response identified {}, expected {}; treating as terminal",
            status.getServiceDeploymentArn(),
            expectedServiceDeploymentArn);
        return result(ExecutionStatus.TERMINAL, status);
      }

      if ("SUCCESSFUL".equals(serviceDeploymentStatus)) {
        return result(ExecutionStatus.SUCCEEDED, status);
      }
      // A configured PAUSE lifecycle hook is the only point where ECS waits for a Continue. The
      // deploy stage is done once the hook awaits action; ECS applies the hook's timeout action
      // if nobody acts, so a downstream Continue stage must exist.
      if (Boolean.TRUE.equals(context.get(WAIT_FOR_LIFECYCLE_GATE))
          && LIFECYCLE_GATE_STATUS.equals(serviceDeploymentStatus)
          && hasHookAwaitingAction(status)) {
        return result(ExecutionStatus.SUCCEEDED, status);
      }
      // A user-requested rollback (Continue with ROLLBACK, Stop) legitimately ends in
      // ROLLBACK_SUCCESSFUL.
      if ("ROLLBACK_SUCCESSFUL".equals(serviceDeploymentStatus)
          && Boolean.TRUE.equals(context.get(ACCEPT_ROLLBACK))) {
        return result(ExecutionStatus.SUCCEEDED, status);
      }
      if (Boolean.TRUE.equals(context.get(WAIT_FOR_STOPPED))
          && "STOPPED".equals(serviceDeploymentStatus)) {
        return result(ExecutionStatus.SUCCEEDED, status);
      }
      if (TERMINAL_FAILURE_STATUSES.contains(serviceDeploymentStatus)) {
        return result(ExecutionStatus.TERMINAL, status);
      }
      // PENDING, IN_PROGRESS, ROLLBACK_REQUESTED, ROLLBACK_IN_PROGRESS, STOP_REQUESTED,
      // STOP_IN_PROGRESS, and unknown future states remain running. In particular, a completed
      // rollback is terminal and can never be accepted as success for the requested deployment.
      return result(ExecutionStatus.RUNNING, status);
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() != HttpStatus.NOT_FOUND.value()) {
        throw e;
      }
      // The service deployment may not be visible immediately after create/update; retry, but
      // only a bounded number of consecutive times (like MonitorKatoTask) so a permanent 404
      // fails instead of looking like a slow rollout until the timeout.
      int notFoundRetryCount = notFoundRetryCount(context) + 1;
      if (notFoundRetryCount >= MAX_NOT_FOUND_RETRIES) {
        log.warn(
            "ecs-native service deployment {} for {} still not found after {} attempts; failing",
            expectedServiceDeploymentArn,
            serverGroupName,
            notFoundRetryCount);
        throw e;
      }
      return TaskResult.builder(ExecutionStatus.RUNNING)
          .context(NOT_FOUND_RETRY_COUNT, notFoundRetryCount)
          .build();
    }
  }

  private static TaskResult result(
      ExecutionStatus executionStatus, EcsServiceDeploymentStatus status) {
    return TaskResult.builder(executionStatus)
        .context("ecsNativeDeploymentStatus", status)
        .context(NOT_FOUND_RETRY_COUNT, 0)
        .build();
  }

  private static int notFoundRetryCount(Map<String, Object> context) {
    Object count = context.get(NOT_FOUND_RETRY_COUNT);
    return count instanceof Number ? ((Number) count).intValue() : 0;
  }

  private static boolean hasHookAwaitingAction(EcsServiceDeploymentStatus status) {
    return status.getLifecycleHookDetails() != null
        && status.getLifecycleHookDetails().stream()
            .anyMatch(hook -> HOOK_AWAITING_ACTION.equals(hook.getStatus()));
  }

  /**
   * Resolves the service deployment this stage must act on or wait for.
   *
   * <p>A stage that itself started a deployment (the deploy's own createServerGroup, or the native
   * rollback) must wait for that deployment, so the ARN in its own clouddriver task result wins.
   * Otherwise a deploy that directly follows another ecs-native deploy would pick up the
   * predecessor's (already SUCCESSFUL, or foreign) ARN from the upstream lookup below.
   *
   * <p>Only stages that do not start a deployment of their own (Continue, Stop, the standalone wait
   * stage) resolve the ARN from the referenced or preceding deploy stage, falling back to an ARN
   * set explicitly on their own context.
   */
  public static String resolveExpectedServiceDeploymentArn(StageExecution stage) {
    List<Map<?, ?>> ownDeployments = ownKatoTaskDeployments(stage.getContext());
    if (!ownDeployments.isEmpty()) {
      // This stage started a deployment itself. Never fall back to an upstream ARN: if
      // clouddriver could not report the new deployment's ARN, failing is safer than gating on a
      // different deployment.
      return ownDeployments.stream()
          .map(WaitForEcsNativeServiceDeploymentTask::deploymentArnFromMetadata)
          .filter(Objects::nonNull)
          .reduce((first, second) -> second)
          .orElse(null);
    }
    return resolveReferencedServiceDeploymentArn(stage);
  }

  /**
   * Collects the {@code deployments} entries from this stage's own clouddriver task results ({@code
   * kato.tasks}, written by MonitorKatoTask), oldest first. Only operations that create or update a
   * server group return deployments, so Continue/Stop yield an empty list here.
   */
  private static List<Map<?, ?>> ownKatoTaskDeployments(Map<String, Object> context) {
    List<Map<?, ?>> deployments = new ArrayList<>();
    for (Object katoTask : asCollection(context.get("kato.tasks"))) {
      if (!(katoTask instanceof Map)) {
        continue;
      }
      for (Object resultObject : asCollection(((Map<?, ?>) katoTask).get("resultObjects"))) {
        if (!(resultObject instanceof Map)) {
          continue;
        }
        for (Object deployment : asCollection(((Map<?, ?>) resultObject).get("deployments"))) {
          if (deployment instanceof Map) {
            deployments.add((Map<?, ?>) deployment);
          }
        }
      }
    }
    return deployments;
  }

  private static Collection<?> asCollection(Object value) {
    return value instanceof Collection ? (Collection<?>) value : List.of();
  }

  private static String deploymentArnFromMetadata(Map<?, ?> deployment) {
    Object metadata = deployment.get("metadata");
    if (!(metadata instanceof Map)) {
      return null;
    }
    Object arn = ((Map<?, ?>) metadata).get(EXPECTED_SERVICE_DEPLOYMENT_ARN);
    return arn instanceof String && !((String) arn).isBlank() ? (String) arn : null;
  }

  private static String resolveReferencedServiceDeploymentArn(StageExecution stage) {
    return findReferencedDeploymentStage(stage)
        .map(WaitForEcsNativeServiceDeploymentTask::deploymentArnOf)
        .orElseGet(() -> deploymentArn(stage.getContext()));
  }

  /**
   * Finds the deploy stage (or, for a Deck-authored {@code deploy} wrapper, its synthetic {@code
   * createServerGroup} child) that published the deployment ARN this stage refers to: the explicit
   * {@code deploymentStageRefId} when set, otherwise the stage's requisite stages.
   */
  private static Optional<StageExecution> findReferencedDeploymentStage(StageExecution stage) {
    Collection<String> references = stage.getRequisiteStageRefIds();
    Object explicitReference = stage.getContext().get("deploymentStageRefId");
    if (explicitReference instanceof String && !((String) explicitReference).isBlank()) {
      references = List.of((String) explicitReference);
    }
    if (references == null) {
      return Optional.empty();
    }
    for (String reference : references) {
      Optional<StageExecution> precedingStage =
          stage.getExecution().getStages().stream()
              .filter(candidate -> reference.equals(candidate.getRefId()))
              .findFirst();
      if (precedingStage.isEmpty()) {
        continue;
      }
      if (deploymentArnOf(precedingStage.get()) != null) {
        return precedingStage;
      }
      // A Deck-authored "deploy" stage wraps one synthetic createServerGroup child per cluster; the
      // ARN is written to that child's outputs, not to the wrapper.
      Optional<StageExecution> child = deploymentChild(stage, precedingStage.get());
      if (child.isPresent()) {
        return child;
      }
    }
    return Optional.empty();
  }

  private static Optional<StageExecution> deploymentChild(
      StageExecution stage, StageExecution parent) {
    return stage.getExecution().getStages().stream()
        .filter(candidate -> parent.getId().equals(candidate.getParentStageId()))
        .filter(child -> deploymentArnOf(child) != null)
        .reduce((first, second) -> second);
  }

  private static String deploymentArnOf(StageExecution stage) {
    String resolved = deploymentArn(stage.getOutputs());
    return resolved != null ? resolved : deploymentArn(stage.getContext());
  }

  private static String deploymentArn(Map<String, Object> values) {
    Object expected = values.get(EXPECTED_SERVICE_DEPLOYMENT_ARN);
    if (expected == null) {
      expected = values.get("expectedServiceDeploymentArn");
    }
    return expected instanceof String && !((String) expected).isBlank() ? (String) expected : null;
  }

  private static String resolveRegion(Map<String, Object> context) {
    String region = (String) context.get("region");
    if (region != null) {
      return region;
    }
    Map<String, List<String>> serverGroupsByRegion = deployServerGroups(context);
    return serverGroupsByRegion == null || serverGroupsByRegion.isEmpty()
        ? null
        : serverGroupsByRegion.keySet().iterator().next();
  }

  private static String resolveServerGroupName(Map<String, Object> context, String region) {
    String serverGroupName = (String) context.get("serverGroupName");
    if (serverGroupName != null) {
      return serverGroupName;
    }
    Map<String, List<String>> serverGroupsByRegion = deployServerGroups(context);
    if (serverGroupsByRegion == null || region == null) {
      return null;
    }
    List<String> serverGroups = serverGroupsByRegion.get(region);
    return serverGroups == null || serverGroups.isEmpty() ? null : serverGroups.get(0);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, List<String>> deployServerGroups(Map<String, Object> context) {
    return (Map<String, List<String>>)
        Optional.ofNullable(context.get("deploy.server.groups")).orElse(null);
  }
}
