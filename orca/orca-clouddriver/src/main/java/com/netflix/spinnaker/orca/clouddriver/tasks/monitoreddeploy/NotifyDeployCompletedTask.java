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
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage;
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorServiceProvider;
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentCompletedRequest;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "monitored-deploy.enabled")
public class NotifyDeployCompletedTask extends MonitoredDeployBaseTask {
  @Autowired
  NotifyDeployCompletedTask(
      DeploymentMonitorServiceProvider deploymentMonitorServiceProvider, Registry registry) {
    super(deploymentMonitorServiceProvider, registry);
  }

  @Override
  public @Nonnull TaskResult executeInternal(
      StageExecution stage, DeploymentMonitorDefinition monitorDefinition) {
    DeploymentCompletedRequest request = new DeploymentCompletedRequest(stage);

    request.setStatus(
        convertStageStatus(
            (boolean) stage.getContext().getOrDefault("hasDeploymentFailed", false)));
    request.setRollback(DeploymentCompletedRequest.DeploymentStatus.ROLLBACK_NOT_PERFORMED);

    // check whether rollback was initiated and successful
    if (stage.getParent() != null) {
      stage.getParent().directChildren().stream()
          .filter(s -> s.getType().equals(RollbackClusterStage.PIPELINE_CONFIG_TYPE))
          .findFirst()
          .ifPresent(
              foundRollbackStage ->
                  request.setRollback(
                      convertStageStatus(
                          foundRollbackStage.getStatus() != ExecutionStatus.SUCCEEDED)));
    }

    Retrofit2SyncCall.executeCall(monitorDefinition.getService().notifyCompleted(request));
    return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED);
  }

  private DeploymentCompletedRequest.DeploymentStatus convertStageStatus(Boolean failedStatus) {
    return failedStatus
        ? DeploymentCompletedRequest.DeploymentStatus.FAILURE
        : DeploymentCompletedRequest.DeploymentStatus.SUCCESS;
  }
}
