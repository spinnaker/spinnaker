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

/**
 * Base for the explicit Continue/Stop tasks that act on one pinned native ECS service deployment.
 *
 * <p>Both submit an operation that must carry the exact {@code
 * ecsNativeExpectedServiceDeploymentArn}, resolved the same way {@link
 * WaitForEcsNativeServiceDeploymentTask} resolves it, so the action and the following wait target
 * the same deployment.
 */
public abstract class EcsNativeServiceDeploymentLifecycleTask extends AbstractServerGroupTask {

  static final String ARN = WaitForEcsNativeServiceDeploymentTask.EXPECTED_SERVICE_DEPLOYMENT_ARN;
  static final String ACCEPT_ROLLBACK = "ecsNativeAcceptRollback";

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public Map convert(StageExecution stage) {
    Map operation = super.convert(stage);
    String expectedArn =
        WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage);
    if (expectedArn != null) {
      operation.put(ARN, expectedArn);
    }
    Object arn = operation.get(ARN);
    if (!(arn instanceof String) || ((String) arn).isBlank()) {
      throw new IllegalArgumentException(
          getServerGroupAction()
              + " requires "
              + ARN
              + " or a preceding deployment stage reference");
    }
    return operation;
  }
}
