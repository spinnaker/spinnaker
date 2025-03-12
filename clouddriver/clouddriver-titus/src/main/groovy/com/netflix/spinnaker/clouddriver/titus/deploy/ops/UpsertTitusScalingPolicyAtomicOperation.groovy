/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.titus.deploy.ops

import com.netflix.spinnaker.clouddriver.orchestration.sagas.AbstractSagaAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.sagas.SagaAtomicOperationBridge
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.MonitorTitusScalingPolicy
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.UpsertTitusScalingPolicy
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertTitusScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusScalingPolicyModified
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusExceptionHandler
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.UpsertTitusScalingPolicyCompletionHandler
import groovy.util.logging.Slf4j
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull

@Slf4j
class UpsertTitusScalingPolicyAtomicOperation extends AbstractSagaAtomicOperation<UpsertTitusScalingPolicyDescription, TitusScalingPolicyModified, Map<String, String>> {
  UpsertTitusScalingPolicyAtomicOperation(UpsertTitusScalingPolicyDescription description) {
    super(description)
  }

  @Override
  protected SagaFlow buildSagaFlow(List priorOutputs) {
    return new SagaFlow()
      .then(UpsertTitusScalingPolicy.class)
      .then(MonitorTitusScalingPolicy.class)
      .exceptionHandler(TitusExceptionHandler.class)
      .completionHandler(UpsertTitusScalingPolicyCompletionHandler.class);
  }

  @Override
  protected void configureSagaBridge(@NotNull @Nonnull SagaAtomicOperationBridge.ApplyCommandWrapper.ApplyCommandWrapperBuilder builder) {
    def build = UpsertTitusScalingPolicy.UpsertTitusScalingPolicyCommand.builder().description(description).build()
    builder.initialCommand(build)
  }

  @Override
  protected Map<String, String> parseSagaResult(@NotNull @Nonnull TitusScalingPolicyModified result) {
    return [scalingPolicyID: result.getScalingPolicyId()]
  }
}
