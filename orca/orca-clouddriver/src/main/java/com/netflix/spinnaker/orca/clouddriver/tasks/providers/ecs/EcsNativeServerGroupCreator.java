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
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * ServerGroupCreator for the second, opt-in ECS provider ("ecs-native").
 *
 * <p>It delegates all operation/artifact/image-resolution logic to the classic {@link
 * EcsServerGroupCreator} bean and differs only in the cloud-provider id it reports, so that a
 * deploy stage declaring {@code cloudProvider: "ecs-native"} is routed here by {@code
 * CreateServerGroupTask} (and, in turn, to the clouddriver {@code @EcsNativeOperation} converters).
 * Clone is not part of this path: an {@code ecs-native} service is a single durable service, so
 * cloning it is rejected by clouddriver ({@code EcsNativeCloneServiceAtomicOperationConverter}) and
 * Deck does not offer the action. The health provider name stays "ecs" because the resulting
 * services are cached and viewed through the existing ECS read stack.
 *
 * <p>Composition rather than inheritance: the classic creator is Groovy and resolves some members
 * dynamically against the runtime class, which breaks for private members when subclassed.
 */
@Component
public class EcsNativeServerGroupCreator implements ServerGroupCreator {

  static final String CLOUD_PROVIDER = "ecs-native";

  private final EcsServerGroupCreator ecsServerGroupCreator;

  public EcsNativeServerGroupCreator(EcsServerGroupCreator ecsServerGroupCreator) {
    this.ecsServerGroupCreator = ecsServerGroupCreator;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public List<Map> getOperations(StageExecution stage) {
    return ecsServerGroupCreator.getOperations(stage);
  }

  @Override
  public boolean isKatoResultExpected() {
    return ecsServerGroupCreator.isKatoResultExpected();
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER;
  }

  @Override
  public Optional<String> getHealthProviderName() {
    return ecsServerGroupCreator.getHealthProviderName();
  }
}
