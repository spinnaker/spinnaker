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
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.MonitoredDeployStageData;
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentCompletedRequest;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
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
      Stage stage,
      MonitoredDeployStageData context,
      DeploymentMonitorDefinition monitorDefinition) {
    // TODO(mvulfson): actually populate the request data
    DeploymentCompletedRequest request = new DeploymentCompletedRequest(stage);
    monitorDefinition.getService().notifyCompleted(request);

    return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED);
  }
}
