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
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorServiceProvider;
import com.netflix.spinnaker.orca.deploymentmonitor.models.EvaluateHealthResponse;
import com.netflix.spinnaker.orca.deploymentmonitor.models.RequestBase;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "monitored-deploy.enabled")
public class NotifyDeployStartingTask extends MonitoredDeployBaseTask {
  @Autowired
  NotifyDeployStartingTask(
      DeploymentMonitorServiceProvider deploymentMonitorServiceProvider, Registry registry) {
    super(deploymentMonitorServiceProvider, registry);
  }

  @Override
  public @Nonnull TaskResult executeInternal(
      StageExecution stage, DeploymentMonitorDefinition monitorDefinition) {
    RequestBase request = new RequestBase(stage);
    EvaluateHealthResponse response =
        Retrofit2SyncCall.execute(monitorDefinition.getService().notifyStarting(request));

    sanitizeAndLogResponse(response, monitorDefinition, stage.getExecution().getId());

    return buildTaskResult(
        processDirective(response.getNextStep().getDirective(), monitorDefinition), response);
  }

  private TaskResult.TaskResultBuilder processDirective(
      EvaluateHealthResponse.NextStepDirective directive,
      DeploymentMonitorDefinition monitorDefinition) {
    switch (directive) {
      case COMPLETE:
        log.warn(
            "COMPLETE response (from {}) is not valid, coercing to CONTINUE", monitorDefinition);
        return TaskResult.builder(ExecutionStatus.SUCCEEDED);

      case CONTINUE:
        return TaskResult.builder(ExecutionStatus.SUCCEEDED);

      case WAIT:
        return TaskResult.builder(ExecutionStatus.RUNNING);

      case ABORT:
        return TaskResult.builder(ExecutionStatus.TERMINAL);

      default:
        throw new DeploymentMonitorInvalidDataException(
            String.format(
                "Invalid next step directive: %s received from %s", directive, monitorDefinition));
    }
  }
}
