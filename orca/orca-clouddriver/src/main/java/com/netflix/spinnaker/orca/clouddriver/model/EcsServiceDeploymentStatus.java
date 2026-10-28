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

package com.netflix.spinnaker.orca.clouddriver.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors clouddriver's ECS service-deployment status DTO. The target task definition and stable
 * service-deployment ARN pin polling to the deployment requested by the stage.
 */
@Data
@NoArgsConstructor
public class EcsServiceDeploymentStatus {
  String serviceName;
  String clusterArn;
  String serviceDeploymentArn;
  String deploymentId;
  String targetServiceRevisionArn;
  String targetTaskDefinition;
  String status;
  String statusReason;
  String lifecycleStage;
  List<LifecycleHook> lifecycleHookDetails;
  String rolloutState;
  String rolloutStateReason;
  Long createdAt;
  Long startedAt;
  Long finishedAt;
  Long updatedAt;

  @Data
  @NoArgsConstructor
  public static class LifecycleHook {
    String hookId;
    String status;
    String targetType;
    Long expiresAt;
    String timeoutAction;
  }
}
