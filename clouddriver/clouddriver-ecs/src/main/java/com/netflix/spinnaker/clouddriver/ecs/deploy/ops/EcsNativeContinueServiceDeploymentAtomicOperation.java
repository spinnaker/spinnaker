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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeServiceDeploymentDescription;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.ecs.model.ContinueServiceDeploymentRequest;
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookAction;
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookDetail;
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookStatus;
import software.amazon.awssdk.services.ecs.model.ServiceDeployment;

/**
 * Acts on the PAUSE lifecycle hook of one pinned native ECS service deployment: resumes it, or
 * rejects it with a rollback. ECS requires the hook id, which is resolved from the deployment's
 * hook that is currently awaiting action.
 */
public class EcsNativeContinueServiceDeploymentAtomicOperation
    extends AbstractEcsNativeServiceDeploymentAtomicOperation {

  public EcsNativeContinueServiceDeploymentAtomicOperation(
      EcsNativeServiceDeploymentDescription description) {
    super(description, "Continue ECS native service deployment");
  }

  @Override
  protected void transition(ServiceDeployment deployment) {
    String actionName =
        StringUtils.defaultIfBlank(description.getLifecycleAction(), "CONTINUE").toUpperCase();
    if (!StringUtils.equalsAny(actionName, "CONTINUE", "ROLLBACK")) {
      throw new IllegalArgumentException("lifecycleAction must be CONTINUE or ROLLBACK");
    }
    getAmazonEcsClient()
        .continueServiceDeployment(
            ContinueServiceDeploymentRequest.builder()
                .serviceDeploymentArn(deployment.serviceDeploymentArn())
                .hookId(awaitingHookId(deployment))
                .action(DeploymentLifecycleHookAction.fromValue(actionName))
                .build());
  }

  private static String awaitingHookId(ServiceDeployment deployment) {
    return deployment.lifecycleHookDetails().stream()
        .filter(hook -> hook.status() == DeploymentLifecycleHookStatus.AWAITING_ACTION)
        .map(DeploymentLifecycleHookDetail::hookId)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Service deployment "
                        + deployment.serviceDeploymentArn()
                        + " has no lifecycle hook awaiting action (status="
                        + deployment.statusAsString()
                        + ", stage="
                        + deployment.lifecycleStageAsString()
                        + "); enable lifecyclePauseStage on the deploy stage"));
  }
}
