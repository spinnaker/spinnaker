/*
 * Copyright 2021 Salesforce, Inc.
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
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskOwner;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.SystemNotification;
import groovy.util.logging.Slf4j;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MonitorDeployManifestTask extends MonitorKatoTask {
  public static final String TASK_NAME = "monitorDeploy";
  private final OortService oortService;
  private static final Logger log = LoggerFactory.getLogger(MonitorDeployManifestTask.class);

  public MonitorDeployManifestTask(
      KatoService katoService,
      OortService oortService,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      RetrySupport retrySupport) {
    super(katoService, registry, dynamicConfigService, retrySupport);
    this.oortService = oortService;
  }

  long maximumPeriodOfInactivity() {
    return getDynamicConfigService()
        .getConfig(
            Long.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.maximum-period-inactivity-ms",
            300000L);
  }

  long maximumForcedRetries() {
    return getDynamicConfigService()
        .getConfig(
            Integer.class,
            "tasks.monitor-kato-task.kubernetes.deploy-manifest.maximum-forced-retries",
            3);
  }

  @Override
  protected void handleClouddriverRetries(
      StageExecution stage, Task katoTask, Map<String, Object> outputs) {
    if (shouldRetryKubernetesTask(stage, katoTask)) {
      try {
        retryKubernetesTask(stage, katoTask, outputs);
      } catch (Exception e) {
        log.debug(
            "exception occurred when attempting to retry task with id: " + katoTask.getId(), e);
      }
    }
  }

  /**
   * Determine if it's appropriate to retry a task
   *
   * @param stage the stage containing the task
   * @param katoTask the task under consideration
   * @return true when task is a kubernetes task and the feature is enabled and previous attempts to
   *     retry didn't fail and there are attempts remaining and the task has been executing longer
   *     than the maximum inactivity period
   */
  private boolean shouldRetryKubernetesTask(StageExecution stage, Task katoTask) {
    String cloudProvider = getCloudProvider(stage);
    if (cloudProvider == null
        || !cloudProvider.equals("kubernetes")
        || !getDynamicConfigService()
            .isEnabled("tasks.monitor-kato-task.kubernetes.deploy-manifest.retry-task", false)) {
      log.info("task: {} did not meet the conditions required for a retry", katoTask.getId());
      return false;
    }

    Boolean hasPreviousRetryFailed =
        (Boolean) stage.getContext().getOrDefault("kato.task.forceRetryFatalError", false);
    if (hasPreviousRetryFailed) {
      log.warn(
          "previous forced retry attempts failed with a terminal error. Will not attempt to retry any further");
      return false;
    }

    Integer retryCount = (Integer) stage.getContext().getOrDefault("kato.task.forcedRetries", 0);
    if (retryCount > maximumForcedRetries()) {
      log.warn(
          "Number of forced retry attempts has exceeded maximum number allowed: {}. Will not "
              + "attempt to retry any further",
          maximumForcedRetries());
      return false;
    }

    Optional<Duration> elapsedTime =
        getCurrentTaskExecutionTime(stage, katoTask, MonitorDeployManifestTask.TASK_NAME);
    if (elapsedTime.isEmpty()) {
      log.debug(
          "error occurred in calculating how long the current task: {} for task id: {} has been running - "
              + "will not attempt to retry",
          MonitorDeployManifestTask.TASK_NAME,
          katoTask.getId());
      return false;
    }

    if (Duration.ofMillis(maximumPeriodOfInactivity()).compareTo(elapsedTime.get()) < 0) {
      log.info(
          "task: {} is eligible for retries as its status has not been updated for some time",
          katoTask.getId());
      return true;
    }
    log.info(
        "the Running task: {} has not yet crossed the configured period of inactivity threshold - not attempting "
            + "to retry",
        katoTask.getId());
    return false;
  }

  /**
   * Restart a kubernetes task if the clouddriver pod that owns the task is no longer present. This
   * assumes that clouddriver itself is running in kubernetes.
   *
   * <p>On return, the following information in the outputs map indicates what happened: -
   * kato.task.forceRetryAttempts incremented, or set it to 1 if previously unset -
   * kato.task.forceRetryFatalError set to true if there's an error - kato.task.forcedRetries
   * incremented if the task was successfully restarted
   *
   * @param stage the stage containing the task
   * @param katoTask the task to retry
   * @param outputs outputs of the task
   */
  private void retryKubernetesTask(
      StageExecution stage, Task katoTask, Map<String, Object> outputs) {
    Integer retryAttempts =
        (Integer) stage.getContext().getOrDefault("kato.task.forcedRetryAttempts", 0) + 1;
    Integer forcedRetries = (Integer) stage.getContext().getOrDefault("kato.task.forcedRetries", 0);
    log.info(
        "retrying kubernetes cloudprovider task with ID: {}. Attempt: {}",
        katoTask.getId(),
        retryAttempts);
    outputs.put("kato.task.forcedRetryAttempts", retryAttempts);
    TaskOwner clouddriverPodName;
    try {
      clouddriverPodName = getKatoService().lookupTaskOwner("kubernetes", katoTask.getId());
    } catch (SpinnakerServerException re) {
      log.warn(
          "failed to retrieve clouddriver owner information from task ID: {}. Retry failed. Error: ",
          katoTask.getId(),
          re);
      outputs.put("kato.task.forceRetryFatalError", true);
      stage.getContext().put("kato.task.forceRetryFatalError", true);
      return;
    }

    if (clouddriverPodName == null || clouddriverPodName.getName() == null) {
      log.warn(
          "failed to retrieve clouddriver owner information for task ID: {}. Retry failed",
          katoTask.getId());
      outputs.put("kato.task.forceRetryFatalError", true);
      stage.getContext().put("kato.task.forceRetryFatalError", true);
      return;
    }

    // manifest will be returned only if it actually exists - which means the
    // owner clouddriver pod is alive and functional. We don't need to force
    // retry in this case.
    String clouddriverAccount =
        getDynamicConfigService()
            .getConfig(
                String.class, "tasks.monitor-kato-task.kubernetes.deploy-manifest.account", "");
    if (clouddriverAccount.isBlank()) {
      log.warn(
          "tasks.monitor-kato-task.kubernetes.deploy-manifest.account is a required property. Retry failed for task: {}",
          katoTask.getId());
      outputs.put("kato.task.forceRetryFatalError", true);
      stage.getContext().put("kato.task.forceRetryFatalError", true);
      return;
    }

    String clouddriverNamespace =
        getDynamicConfigService()
            .getConfig(
                String.class,
                "tasks.monitor-kato-task.kubernetes.deploy-manifest.namespace",
                "spinnaker");

    try {
      Manifest _ignored =
          oortService.getManifest(
              clouddriverAccount,
              clouddriverNamespace,
              "pod " + clouddriverPodName.getName(),
              false);
      log.info(
          "task ID: {} owner: {} is up and running. No need to force a retry of the task",
          katoTask.getId(),
          clouddriverPodName);
    } catch (Exception e) {
      log.warn(
          "exception occurred while attempting to lookup task: {} owner clouddriver information",
          katoTask.getId(),
          e);
      if (e instanceof SpinnakerHttpException) {
        SpinnakerHttpException spinnakerHttpException = (SpinnakerHttpException) e;
        // only attempt a retry if clouddriver owner pod manifest results in a 404
        if (spinnakerHttpException.getResponseCode() == HttpStatus.NOT_FOUND.value()) {
          log.info(
              "Since task ID {} owner: {} manifest not found, attempting to force retry task execution",
              katoTask.getId(),
              clouddriverPodName.getName());
          ((PipelineExecutionImpl) stage.getExecution())
              .getSystemNotifications()
              .add(
                  new SystemNotification(
                      getClock().millis(),
                      "katoRetryTask",
                      "Issue detected with the current clouddriver owner of the task. Retrying "
                          + "downstream cloud provider operation",
                      false));
          try {
            // we need to reschedule the task so that it can be picked up by a different clouddriver
            // pod
            getKatoService().updateTaskRetryability("kubernetes", katoTask.getId(), true);
            getKatoService()
                .restartTask("kubernetes", katoTask.getId(), ImmutableList.of(getOperation(stage)));
            log.info(
                "task: {} has been successfully rescheduled on another clouddriver pod",
                katoTask.getId());
            outputs.put("kato.task.forcedRetries", forcedRetries + 1);
            stage.getContext().put("kato.task.forcedRetries", forcedRetries + 1);
          } catch (Exception clouddriverException) {
            log.warn(
                "Attempt failed to retry task with id: {}", katoTask.getId(), clouddriverException);
            outputs.put("kato.task.forceRetryFatalError", true);
            stage.getContext().put("kato.task.forceRetryFatalError", true);
          }
        }
      }
    }
  }

  /**
   * Determine the operation map necessary for restarting the DeployManifestTask task in a stage.
   */
  @VisibleForTesting
  public ImmutableMap<String, Map> getOperation(StageExecution stage) {
    if (!hasSuccessfulTask(stage, DeployManifestTask.TASK_NAME)) {
      throw new IllegalStateException(
          "getOperation requires a successful " + DeployManifestTask.TASK_NAME + " task");
    }

    return DeployManifestTask.getOperation(stage);
  }
}
