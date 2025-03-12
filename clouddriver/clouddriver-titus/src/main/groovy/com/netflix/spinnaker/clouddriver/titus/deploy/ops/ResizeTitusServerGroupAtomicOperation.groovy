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
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.ResizeTitusJob
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ResizeTitusServerGroupDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusExceptionHandler
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull

class ResizeTitusServerGroupAtomicOperation extends AbstractSagaAtomicOperation<ResizeTitusServerGroupDescription, Void, Void> {
  ResizeTitusServerGroupAtomicOperation(ResizeTitusServerGroupDescription description) {
    super(description)
  }

  @Override
  protected SagaFlow buildSagaFlow(List priorOutputs) {
    return new SagaFlow()
      .then(ResizeTitusJob.class)
      .exceptionHandler(TitusExceptionHandler.class)
  }

  @Override
  protected void configureSagaBridge(@NotNull @Nonnull SagaAtomicOperationBridge.ApplyCommandWrapper.ApplyCommandWrapperBuilder builder) {
    builder.initialCommand(
      ResizeTitusJob.ResizeTitusJobCommand.builder().description(description).build()
    )
  }

  @Override
  protected Void parseSagaResult(@NotNull @Nonnull Void result) {
    return null
  }
}
