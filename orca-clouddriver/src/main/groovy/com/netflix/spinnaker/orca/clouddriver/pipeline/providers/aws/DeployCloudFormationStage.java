/*
 * Copyright (c) 2019 Schibsted Media Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws;

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation.CloudFormationForceCacheRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation.DeployCloudFormationTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation.WaitForCloudFormationCompletionTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
public class DeployCloudFormationStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "deployCloudFormation";

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask(DeployCloudFormationTask.TASK_NAME, DeployCloudFormationTask.class)
      .withTask("monitorCloudFormation", MonitorKatoTask.class)
      .withTask("forceRefreshCache", CloudFormationForceCacheRefreshTask.class)
      .withTask("waitForCloudFormationCompletion", WaitForCloudFormationCompletionTask.class);
  }
}
