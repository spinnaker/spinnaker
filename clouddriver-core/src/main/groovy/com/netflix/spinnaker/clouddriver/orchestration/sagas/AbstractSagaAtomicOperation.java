/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.orchestration.sagas;

import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import com.netflix.spinnaker.clouddriver.orchestration.SagaContextAware;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.SagaAtomicOperationBridge.ApplyCommandWrapper;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.SagaAtomicOperationBridge.ApplyCommandWrapper.ApplyCommandWrapperBuilder;
import com.netflix.spinnaker.clouddriver.saga.SagaService;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Removes some of the boilerplate for AtomicOperations to use Sagas.
 *
 * @param <T> The AtomicOperation description
 * @param <SR> The saga result type
 * @param <R> The operation result type
 */
public abstract class AbstractSagaAtomicOperation<T, SR, R>
    implements AtomicOperation<R>, SagaContextAware {

  /**
   * Needs to be an autowired property due to how the {@link OrchestrationProcessor} creates
   * AtomicOperations.
   */
  @Autowired private SagaService sagaService;

  protected T description;
  private SagaContext sagaContext;

  public AbstractSagaAtomicOperation(T description) {
    this.description = description;
  }

  /** Build the {@link SagaAction} for the AtomicOperation. */
  @Nonnull
  protected abstract SagaFlow buildSagaFlow(List priorOutputs);

  /** Implementing classes will need to configure {@code initialCommand} at minimum. */
  protected abstract void configureSagaBridge(@Nonnull ApplyCommandWrapperBuilder builder);

  /**
   * Provides the opportunity to convert a {@link SagaAction.Result} into the expected result type
   * of the AtomicOperation.
   */
  protected abstract R parseSagaResult(@Nonnull SR result);

  @Override
  public R operate(List priorOutputs) {
    Objects.requireNonNull(sagaContext, "A saga context must be provided");

    SagaFlow flow = buildSagaFlow(priorOutputs);

    ApplyCommandWrapperBuilder builder =
        ApplyCommandWrapper.builder()
            .sagaName(this.getClass().getSimpleName())
            .inputDescription(description)
            .priorOutputs(priorOutputs)
            .sagaContext(sagaContext)
            .task(TaskRepository.threadLocalTask.get())
            .sagaFlow(flow);

    configureSagaBridge(builder);

    // TODO(rz): Should make SagaAtomicOperationBridge a bean and inject that instead
    SR result =
        new SagaAtomicOperationBridge(sagaService, sagaContext.getSagaId()).apply(builder.build());

    return parseSagaResult(result);
  }

  @Override
  public void setSagaContext(@Nonnull SagaContext sagaContext) {
    this.sagaContext = sagaContext;
  }

  @Nullable
  @Override
  public SagaContext getSagaContext() {
    return sagaContext;
  }
}
