/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.data.task.SagaId;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.orchestration.SagaContextAware.SagaContext;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.SagaService;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;

/**
 * A helper class to reduce boilerplate code while integrating Sagas into existing AtomicOperations.
 */
public class SagaAtomicOperationBridge {

  private final SagaService sagaService;
  private final String sagaId;

  public SagaAtomicOperationBridge(SagaService sagaService, String sagaId) {
    this.sagaService = sagaService;
    this.sagaId = sagaId;
  }

  public <T> T apply(@Nonnull ApplyCommandWrapper applyCommand) {
    final SagaContext sagaContext = applyCommand.sagaContext;
    final Task task = applyCommand.task;
    final String sagaName = applyCommand.sagaName;

    // use a random uuid to guarantee a unique saga id (rather than task.getId() or
    // task.getRequestId()). A sagaId may be provided at construct time due to retries.
    final String sagaId =
        Optional.ofNullable(this.sagaId).orElseGet(() -> UUID.randomUUID().toString());

    task.addSagaId(SagaId.builder().id(sagaId).name(sagaName).build());

    applyCommand.sagaFlow.injectFirst(SnapshotAtomicOperationInput.class);

    return sagaService.applyBlocking(
        sagaName,
        sagaId,
        applyCommand.sagaFlow,
        SnapshotAtomicOperationInput.SnapshotAtomicOperationInputCommand.builder()
            .cloudProvider(sagaContext.getCloudProvider())
            .descriptionName(sagaContext.getDescriptionName())
            .descriptionInput(sagaContext.getOriginalInput())
            .description(applyCommand.inputDescription)
            .priorOutputs(applyCommand.priorOutputs)
            .nextCommand(applyCommand.initialCommand)
            .build());
  }

  @Builder
  public static class ApplyCommandWrapper {
    @Nonnull private String sagaName;
    @Nonnull private SagaContext sagaContext;
    @Nonnull private Task task;
    @Nonnull private Object inputDescription;
    @Nonnull private SagaFlow sagaFlow;
    @Nonnull private SagaCommand initialCommand;
    @Nullable private List priorOutputs;
  }
}
