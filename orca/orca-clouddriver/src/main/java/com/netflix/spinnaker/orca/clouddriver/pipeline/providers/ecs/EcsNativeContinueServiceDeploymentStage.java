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
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs.EcsNativeContinueServiceDeploymentTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs.WaitForEcsNativeServiceDeploymentTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/** Continues a pinned native ECS deployment through its bake/test-traffic gate. */
@Component
public class EcsNativeContinueServiceDeploymentStage implements StageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE =
      StageDefinitionBuilder.getType(EcsNativeContinueServiceDeploymentStage.class);

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("continueServiceDeployment", EcsNativeContinueServiceDeploymentTask.class)
        .withTask("monitorServiceDeployment", MonitorKatoTask.class)
        .withTask(
            WaitForEcsNativeServiceDeploymentTask.TASK_NAME,
            WaitForEcsNativeServiceDeploymentTask.class);
  }
}
