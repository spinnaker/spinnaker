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

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.monitoreddeploy.EvaluateDeploymentHealthTask;
import javax.annotation.Nonnull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * NOTE: This stage is part of the monitored deploy strategy
 *
 * <p>This stage is used to consult with the deployment monitor about the state of the deployment.
 * This stage is inserted after each deployment step is completed, e.g. 10%, 50%, 100%. The order of
 * operations is (e.g. for 50%): 1. Enable 50% of traffic to the new ASG 2. Disable 50% of traffic
 * from the old ASG 3. Ask the deployment monitor to evaluate health of newly deployed/enabled
 * instances
 *
 * <p>The deployment monitor can respond with a number of messages including: wait, abort, continue,
 * complete. Based on the response orca will decide how to proceed with the deployment (see
 * readme.md under orca-deploymentmonitor for details on what these mean)
 */
@Component
@ConditionalOnProperty(value = "monitored-deploy.enabled")
public class EvaluateDeploymentHealthStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE =
      StageDefinitionBuilder.getType(EvaluateDeploymentHealthStage.class);

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("evaluateDeploymentHealth", EvaluateDeploymentHealthTask.class);
  }
}
