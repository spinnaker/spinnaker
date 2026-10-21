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

package com.netflix.spinnaker.clouddriver.ecs;

import com.netflix.spinnaker.clouddriver.core.CloudProvider;
import java.lang.annotation.Annotation;
import org.springframework.stereotype.Component;

/**
 * A second, opt-in Amazon ECS cloud provider that delegates rollout to ECS's native deployment
 * lifecycle (in-place service updates, configurable deployment configuration and circuit-breaker
 * rollback, deployment alarms, native blue/green, and observable service deployments).
 *
 * <p>It exists alongside the original {@link EcsCloudProvider} ({@code "ecs"}) rather than
 * replacing it: users choose it per pipeline by selecting an {@code ecs-native} deploy/clone stage.
 * The running ECS resources are the same objects, so this provider reuses the existing ECS caching
 * agents and cluster/load-balancer/instance views; only the deploy/write path is new.
 */
@Component
public class EcsNativeCloudProvider implements CloudProvider {

  public static final String ID = "ecs-native";

  private final String id = ID;

  private final String displayName = "Amazon-ECS-Native";

  private final Class<? extends Annotation> operationAnnotationType = EcsNativeOperation.class;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public Class<? extends Annotation> getOperationAnnotationType() {
    return operationAnnotationType;
  }
}
