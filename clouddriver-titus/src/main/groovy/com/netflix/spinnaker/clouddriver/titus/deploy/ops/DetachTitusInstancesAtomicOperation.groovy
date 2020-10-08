/*
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.DetachTitusTasks
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DetachTitusInstancesDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusExceptionHandler
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull

class DetachTitusInstancesAtomicOperation extends AbstractSagaAtomicOperation<DetachTitusInstancesDescription, Void, Void> {
  DetachTitusInstancesAtomicOperation(DetachTitusInstancesDescription description) {
    super(description)
  }

  @Override
  protected SagaFlow buildSagaFlow(List priorOutputs) {
    return new SagaFlow()
      .then(DetachTitusTasks.class)
      .exceptionHandler(TitusExceptionHandler.class)
  }

  @Override
  protected void configureSagaBridge(@NotNull @Nonnull SagaAtomicOperationBridge.ApplyCommandWrapper.ApplyCommandWrapperBuilder builder) {
    builder.initialCommand(
      DetachTitusTasks.DetachTitusTasksCommand.builder().description(description).build()
    )
  }

  @Override
  protected Void parseSagaResult(@NotNull @Nonnull Void result) {
    return null
  }
}
