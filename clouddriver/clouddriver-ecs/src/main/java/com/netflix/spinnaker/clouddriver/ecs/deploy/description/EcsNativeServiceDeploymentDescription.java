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

package com.netflix.spinnaker.clouddriver.ecs.deploy.description;

import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Identifies one native ECS service deployment for an explicit lifecycle transition. */
@Data
@EqualsAndHashCode(callSuper = true)
public class EcsNativeServiceDeploymentDescription extends ModifyServiceDescription {
  /** ECS cluster containing the service. Resolved from the service name when omitted. */
  @Nullable String ecsClusterName;

  /** Exact ARN returned by the native ECS service deployment write. */
  String ecsNativeExpectedServiceDeploymentArn;

  /**
   * Continue only: {@code CONTINUE} (default) resumes the paused deployment, {@code ROLLBACK}
   * rejects it and rolls back.
   */
  @Nullable String lifecycleAction;
}
