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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.AbstractServerGroupTask;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rolls an {@code ecs-native} server group back to an earlier task-definition revision.
 *
 * <p>The classic ECS rollback (see {@code ExplicitRollback}) re-enables a disabled versioned server
 * group and disables the current one -- a model that only works when a deploy produces a new
 * versioned service each time. An {@code ecs-native} service is a single durable service, so
 * "rolling back" instead means pointing that service at an earlier task-definition revision of the
 * same family. That is exactly what the native in-place update does, so this task submits the same
 * {@code updateLaunchConfig} operation the {@code ecs-native} provider already routes to its native
 * {@code UpdateService} converter, with the chosen {@code taskDefinition} and a forced new
 * deployment.
 *
 * <p>Reuses {@link AbstractServerGroupTask}: the {@code cloudProvider} ({@code ecs-native}) on the
 * stage context routes the operation to the {@code @EcsNativeOperation} converter, and the base
 * task publishes {@code deploy.server.groups} so the following {@code
 * waitForEcsNativeServiceDeployment} task can gate on the resulting ECS service deployment.
 */
@Component
public class EcsNativeRollbackServerGroupTask extends AbstractServerGroupTask {

  // "updateLaunchConfig" is the clouddriver AtomicOperations name; for cloudProvider ecs-native it
  // routes to EcsNativeUpdateServiceAtomicOperationConverter. Orca refers to it by the plain string
  // (as UpdateLaunchConfigStage/Task do) rather than the clouddriver-core constant, which isn't on
  // orca's classpath.
  static final String OPERATION = "updateLaunchConfig";
  static final String TASK_DEFINITION = "taskDefinition";

  @Override
  public String getServerGroupAction() {
    return OPERATION;
  }

  /**
   * A rollback without a target revision would be an in-place update to "nothing", which the
   * clouddriver converter would turn into a redeploy of whatever the description defaults to. Fail
   * here with a clear message instead.
   */
  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public Map convert(StageExecution stage) {
    Map operation = super.convert(stage);
    Object taskDefinition = operation.get(TASK_DEFINITION);
    if (!(taskDefinition instanceof String) || ((String) taskDefinition).isBlank()) {
      throw new IllegalArgumentException(
          "ecs-native rollback requires "
              + TASK_DEFINITION
              + " (the task-definition revision ARN to roll the service back to)");
    }
    return operation;
  }
}
