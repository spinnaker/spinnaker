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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.ecs;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs.WaitForEcsNativeServiceDeploymentTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * Standalone stage that waits for a pinned {@code ecs-native} service deployment (see {@link
 * WaitForEcsNativeServiceDeploymentTask}).
 *
 * <p>The {@code ecs-native} deploy ({@code createServerGroup}) stage already runs the same wait
 * task as part of its own task graph. This stage is for pipelines that need an explicit, separate
 * wait, e.g. after a deploy whose lifecycle-pause gate let the deploy stage finish early, or to
 * wait on a deployment referenced via {@code deploymentStageRefId} / an explicit {@code
 * ecsNativeExpectedServiceDeploymentArn}.
 */
@Component
public class WaitForEcsNativeServiceDeploymentStage implements StageDefinitionBuilder {
  public static final String STAGE_TYPE = "waitForEcsNativeServiceDeployment";

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask(
        "waitForEcsNativeServiceDeployment", WaitForEcsNativeServiceDeploymentTask.class);
  }
}
