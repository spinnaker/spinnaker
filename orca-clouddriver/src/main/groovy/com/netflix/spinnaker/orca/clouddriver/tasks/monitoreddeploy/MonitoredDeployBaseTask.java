/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.monitoreddeploy;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.DeploymentMonitorDefinition;
import com.netflix.spinnaker.config.DeploymentMonitorServiceProvider;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.MonitoredDeployStageData;
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentStep;
import com.netflix.spinnaker.orca.deploymentmonitor.models.EvaluateHealthResponse;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RetrofitError;

public class MonitoredDeployBaseTask implements RetryableTask {
  private static final int MAX_RETRY_COUNT = 3;
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected DeploymentMonitorDefinition monitorDefinition;
  protected Stage stage;
  protected Registry registry;

  private DeploymentMonitorServiceProvider deploymentMonitorServiceProvider;

  MonitoredDeployBaseTask(
      DeploymentMonitorServiceProvider deploymentMonitorServiceProvider, Registry registry) {
    this.deploymentMonitorServiceProvider = deploymentMonitorServiceProvider;
    this.registry = registry;
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.MINUTES.toMillis(1);
  }

  @Override
  public long getTimeout() {
    // TODO(mvulfson): Use DeploymentMonitorDefinition
    return TimeUnit.MINUTES.toMillis(30);
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    MonitoredDeployStageData context = stage.mapTo(MonitoredDeployStageData.class);

    try {
      this.stage = stage;
      this.monitorDefinition =
          deploymentMonitorServiceProvider.getDefinitionById(
              context.getDeploymentMonitor().getId());

      return executeInternal();
    } catch (RetrofitError e) {

      return handleError(context, e, true);
    } catch (DeploymentMonitorInvalidDataException e) {

      return handleError(context, e, false);
    } catch (Exception e) {
      log.error("Exception while executing {}, aborting deployment", getClass().getSimpleName(), e);

      // TODO(mvulfson): I don't love this
      throw e;
    }
  }

  public @Nonnull TaskResult executeInternal() {
    throw new UnsupportedOperationException("Must implement executeInternal method");
  }

  private TaskResult handleError(
      MonitoredDeployStageData context, Exception e, boolean retryAllowed) {
    registry
        .counter("deploymentMonitor.errors", "monitorId", monitorDefinition.getId())
        .increment();

    if (retryAllowed) {
      int currentRetryCount = context.getDeployMonitorHttpRetryCount();

      if (currentRetryCount < MAX_RETRY_COUNT) {
        log.warn(
            "Failed to get valid response for {} from deployment monitor {}, will retry",
            getClass().getSimpleName(),
            monitorDefinition,
            e);

        return TaskResult.builder(ExecutionStatus.RUNNING)
            .context("deployMonitorHttpRetryCount", ++currentRetryCount)
            .build();
      }
    }

    if (monitorDefinition.isFailOnError()) {
      registry
          .counter("deploymentMonitor.fatalErrors", "monitorId", monitorDefinition.getId())
          .increment();

      log.error(
          "Failed to get valid response for {} from deployment monitor {}, aborting because the monitor is marked with failOnError",
          getClass().getSimpleName(),
          monitorDefinition,
          e);

      return TaskResult.builder(ExecutionStatus.TERMINAL)
          // TODO(mvulfson)
          // .context()
          .build();
    }

    log.warn(
        "Failed to get valid response for {} from deployment monitor {}, ignoring failure",
        getClass().getSimpleName(),
        monitorDefinition,
        e);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        // TODO(mvulfson)
        // .context()
        .build();
  }

  void sanitizeAndLogResponse(EvaluateHealthResponse response) {
    if (response.getNextStep() == null) {
      log.error("Deployment monitor {}: returned null nextStep", monitorDefinition);

      DeploymentStep step = new DeploymentStep();
      step.setDirective(EvaluateHealthResponse.NextStepDirective.UNSPECIFIED);

      response.setNextStep(step);
    }

    if (response.getNextStep().getDirective() == null) {
      log.error("Deployment monitor {}: returned null nextStep.directive", monitorDefinition);

      response.getNextStep().setDirective(EvaluateHealthResponse.NextStepDirective.UNSPECIFIED);
    }

    EvaluateHealthResponse.NextStepDirective nextStepDirective =
        response.getNextStep().getDirective();

    switch (nextStepDirective) {
      case ABORT:
      case COMPLETE:
      case WAIT:
        log.warn(
            "Deployment monitor {}: {} deployment in response to {} for {}",
            monitorDefinition,
            nextStepDirective,
            this.getClass().getSimpleName(),
            stage.getExecution().getId());
        break;

      case CONTINUE:
        log.info(
            "Deployment monitor {}: {} deployment in response to {} for {}",
            monitorDefinition,
            nextStepDirective,
            this.getClass().getSimpleName(),
            stage.getExecution().getId());
        break;

      default:
        log.error(
            "Invalid next step directive: {} received from Deployment Monitor: {}",
            nextStepDirective,
            monitorDefinition);
        break;
    }
  }
}
