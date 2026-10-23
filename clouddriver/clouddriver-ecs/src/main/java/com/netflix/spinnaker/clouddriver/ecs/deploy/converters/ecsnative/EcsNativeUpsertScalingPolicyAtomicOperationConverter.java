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

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters.ecsnative;

import com.netflix.spinnaker.clouddriver.ecs.EcsNativeOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.UpsertScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;

@EcsNativeOperation(AtomicOperations.UPSERT_SCALING_POLICY)
@Component("ecsNativeUpsertScalingPolicy")
public class EcsNativeUpsertScalingPolicyAtomicOperationConverter
    extends AbstractUnsupportedEcsNativeAtomicOperationConverter<UpsertScalingPolicyDescription> {

  public EcsNativeUpsertScalingPolicyAtomicOperationConverter() {
    super(
        UpsertScalingPolicyDescription.class, "Native ECS scaling policy upsert is not supported.");
  }
}
