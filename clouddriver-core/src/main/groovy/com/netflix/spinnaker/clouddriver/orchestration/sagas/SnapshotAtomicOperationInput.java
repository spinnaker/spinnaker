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

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.stereotype.Component;

/**
 * Compatibility bridge for Tasks. If Clouddriver had a Saga-only orchestration system, this step
 * would not be necessary, but might be beneficial for debugging.
 */
@Component
public class SnapshotAtomicOperationInput
    implements SagaAction<SnapshotAtomicOperationInput.SnapshotAtomicOperationInputCommand> {

  @Nonnull
  @Override
  public Result apply(@Nonnull SnapshotAtomicOperationInputCommand command, @Nonnull Saga saga) {
    // We happily don't need to do anything here. This action just snapshots our input data.
    return new Result(command.nextCommand, Collections.emptyList());
  }

  @Builder(builderClassName = "SnapshotAtomicOperationInputCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          SnapshotAtomicOperationInputCommand.SnapshotAtomicOperationInputCommandBuilder.class)
  @JsonTypeName("snapshotAtomicOperationInputCommand")
  @Value
  public static class SnapshotAtomicOperationInputCommand implements SagaCommand {
    @Nonnull private String descriptionName;
    @Nullable private String cloudProvider;
    @Nonnull private Map descriptionInput;
    @Nonnull private Object description;
    @Nonnull private List priorOutputs;
    @Nonnull private SagaCommand nextCommand;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(@Nonnull EventMetadata eventMetadata) {
      this.metadata = eventMetadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class SnapshotAtomicOperationInputCommandBuilder {}
  }
}
