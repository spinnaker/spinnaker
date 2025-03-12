/*
 * Copyright 2024 Harness, Inc.
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
package com.netflix.spinnaker.orca.front50.scheduling;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for polling and disabling unused pipelines in Spinnaker. It extends the
 * AbstractPollingNotificationAgent and uses a scheduled polling mechanism to check for unused
 * pipelines and sends a request to Front50 to disable them if they have not been executed within a
 * specified threshold.
 */
@Component
@ConditionalOnExpression(
    "${pollers.unused-pipelines-disable.enabled:false} && ${execution-repository.sql.enabled:false}")
public class UnusedPipelineDisablePollingNotificationAgent
    extends AbstractPollingNotificationAgent {

  /** Service to interact with Front50 for pipeline operations. */
  Front50Service front50service;

  /** List of completed execution statuses. */
  private static final List<String> COMPLETED_STATUSES =
      ExecutionStatus.COMPLETED.stream().map(Enum::toString).collect(Collectors.toList());

  /** Logger instance for logging events. */
  private final Logger log =
      LoggerFactory.getLogger(UnusedPipelineDisablePollingNotificationAgent.class);

  /** Clock instance for time-based operations. */
  private final Clock clock;

  /** Repository for execution data. */
  private final ExecutionRepository executionRepository;

  /** Registry for metrics and monitoring. */
  private final Registry registry;

  /** Polling interval in seconds. */
  private final long pollingIntervalSec;

  /** Threshold in days to consider a pipeline as unused. */
  private final int thresholdDays;

  /**
   * Flag to indicate if the operation is a dry run. In dryRun mode the intention to disable is
   * logged but not executed.
   */
  private final boolean dryRun;

  /** Timer ID for long task timer. */
  private final Id timerId;

  /**
   * Constructor to initialize the agent with required dependencies.
   *
   * @param clusterLock the cluster lock for notification
   * @param executionRepository the repository for execution data
   * @param front50Service the service to interact with Front50
   * @param clock the clock instance for time-based operations
   * @param registry the registry for metrics and monitoring
   * @param pollingIntervalSec the polling interval in seconds
   * @param thresholdDays the threshold in days since the last execution to consider a pipeline as
   *     unused
   * @param dryRun flag to indicate if the operation is a dry run
   */
  @Autowired
  public UnusedPipelineDisablePollingNotificationAgent(
      NotificationClusterLock clusterLock,
      ExecutionRepository executionRepository,
      Front50Service front50Service,
      Clock clock,
      Registry registry,
      @Value("${pollers.unused-pipelines-disable.interval-sec:3600}") long pollingIntervalSec,
      @Value("${pollers.unused-pipelines-disable.threshold-days:365}") int thresholdDays,
      @Value("${pollers.unused-pipelines-disable.dry-run:true}") boolean dryRun) {
    super(clusterLock);
    this.executionRepository = executionRepository;
    this.clock = clock;
    this.registry = registry;
    this.pollingIntervalSec = pollingIntervalSec;
    this.thresholdDays = thresholdDays;
    this.dryRun = dryRun;
    this.front50service = front50Service;

    timerId = registry.createId("pollers.unusedPipelineDisable.timing");
  }

  /**
   * Returns the polling interval in milliseconds.
   *
   * @return the polling interval in milliseconds
   */
  @Override
  protected long getPollingInterval() {
    return pollingIntervalSec * 1000;
  }

  /**
   * Returns the notification type for this agent.
   *
   * @return the notification type
   */
  @Override
  protected String getNotificationType() {
    return UnusedPipelineDisablePollingNotificationAgent.class.getSimpleName();
  }

  /**
   * The main logic for polling and disabling unused pipelines. It retrieves all application names
   * from Front50, checks for pipelines that have not been executed since the thresholdDays, and
   * sends a request to Front50 to disable them if necessary.
   */
  @Override
  protected void tick() {
    LongTaskTimer timer = registry.longTaskTimer(timerId);
    long timerId = timer.start();
    try {
      executionRepository
          .retrieveAllApplicationNames(PIPELINE)
          .forEach(
              app -> {
                log.debug("Evaluating " + app + " for unused pipelines");
                List<String> pipelineConfigIds =
                    front50service.getPipelines(app, false, true).stream()
                        .map(p -> (String) p.get("id"))
                        .collect(Collectors.toList());

                ExecutionRepository.ExecutionCriteria criteria =
                    new ExecutionRepository.ExecutionCriteria();
                criteria.setStatuses(COMPLETED_STATUSES);
                criteria.setStartTimeCutoff(
                    clock.instant().atZone(ZoneOffset.UTC).minusDays(thresholdDays).toInstant());

                List<String> orcaExecutionsPipelineConfigIds =
                    executionRepository.retrievePipelineConfigIdsForApplicationWithCriteria(
                        app, criteria);

                disableAppPipelines(app, orcaExecutionsPipelineConfigIds, pipelineConfigIds);
              });
    } catch (Exception e) {
      log.error("Disabling pipelines failed", e);
    } finally {
      timer.stop(timerId);
    }
  }

  /**
   * Disables pipelines for a given application if they have not been executed within the threshold
   * days.
   *
   * @param app the application name
   * @param orcaExecutionsPipelineConfigIds the list of pipeline config IDs that have been executed
   * @param front50PipelineConfigIds the list of pipeline config IDs from Front50
   */
  public void disableAppPipelines(
      String app,
      List<String> orcaExecutionsPipelineConfigIds,
      List<String> front50PipelineConfigIds) {

    List<String> front50PipelineConfigIdsNotExecuted =
        front50PipelineConfigIds.stream()
            .filter(p -> !orcaExecutionsPipelineConfigIds.contains(p))
            .collect(Collectors.toList());

    log.info(
        "Found "
            + front50PipelineConfigIdsNotExecuted.size()
            + " pipelines to disable for Application "
            + app);
    front50PipelineConfigIdsNotExecuted.forEach(
        p -> {
          if (!dryRun) {
            log.debug("Disabling pipeline execution " + p);
            disableFront50PipelineConfigId(p);
          } else {
            log.info("DryRun mode: Disabling pipeline execution " + p);
          }
        });
  }

  /**
   * Disables a specific pipeline config ID in Front50.
   *
   * @param pipelineConfigId the pipeline config ID to disable
   */
  public void disableFront50PipelineConfigId(String pipelineConfigId) {
    Map<String, Object> pipeline = front50service.getPipeline(pipelineConfigId);
    if (pipeline.get("disabled") == null || !(boolean) pipeline.get("disabled")) {
      pipeline.put("disabled", true);
      try {
        front50service.updatePipeline(pipelineConfigId, pipeline);
      } catch (SpinnakerHttpException e) {
        if (Arrays.asList(404, 403).contains(e.getResponseCode())) {
          log.warn("Failed to disable pipeline " + pipelineConfigId + " due to " + e.getMessage());
        } else {
          throw e;
        }
      }
    }
  }
}
