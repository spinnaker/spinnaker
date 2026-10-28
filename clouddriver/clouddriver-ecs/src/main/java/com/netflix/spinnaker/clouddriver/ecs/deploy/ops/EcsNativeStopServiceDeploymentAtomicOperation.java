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
import software.amazon.awssdk.services.ecs.model.ServiceDeployment;
import software.amazon.awssdk.services.ecs.model.StopServiceDeploymentRequest;
import software.amazon.awssdk.services.ecs.model.StopServiceDeploymentStopType;

/** Explicitly stops one pinned native ECS service deployment. */
public class EcsNativeStopServiceDeploymentAtomicOperation
    extends AbstractEcsNativeServiceDeploymentAtomicOperation {

  public EcsNativeStopServiceDeploymentAtomicOperation(
      EcsNativeServiceDeploymentDescription description) {
    super(description, "Stop ECS native service deployment");
  }

  @Override
  protected void transition(ServiceDeployment deployment) {
    // ABORT is in the API schema but real ECS rejects it (UnsupportedFeatureException); ROLLBACK is
    // the only supported stop type and works even when rollback was not configured.
    getAmazonEcsClient()
        .stopServiceDeployment(
            StopServiceDeploymentRequest.builder()
                .serviceDeploymentArn(deployment.serviceDeploymentArn())
                .stopType(StopServiceDeploymentStopType.ROLLBACK)
                .build());
  }
}
