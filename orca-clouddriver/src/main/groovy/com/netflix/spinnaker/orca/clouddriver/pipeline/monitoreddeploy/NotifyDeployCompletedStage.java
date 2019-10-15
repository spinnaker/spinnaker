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

package com.netflix.spinnaker.orca.clouddriver.pipeline.monitoreddeploy;

import com.netflix.spinnaker.orca.clouddriver.tasks.monitoreddeploy.NotifyDeployCompletedTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * NOTE: This stage is part of the monitored deploy strategy
 *
 * <p>This stage produces a notification (REST call) to the deployment monitor specified by the user
 * to indicate that the deployment is now complete. This is a purely informational message to the
 * monitor and it may choose to ignore it On the other hand, if the monitor needs to perform any
 * clean up this call can be used for that (also see readme.md under orca-deploymentmonitor)
 */
@Component
@ConditionalOnProperty(value = "monitored-deploy.enabled")
public class NotifyDeployCompletedStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE =
      StageDefinitionBuilder.getType(NotifyDeployCompletedStage.class);

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("notifyDeployCompleted", NotifyDeployCompletedTask.class);
  }
}
