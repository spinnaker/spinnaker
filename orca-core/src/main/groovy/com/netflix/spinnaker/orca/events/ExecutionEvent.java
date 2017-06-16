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

package com.netflix.spinnaker.orca.events;

import java.time.Instant;
import javax.annotation.Nonnull;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import org.springframework.context.ApplicationEvent;

/**
 * Events emitted at various stages in the lifecycle of an execution.
 */
public abstract class ExecutionEvent extends ApplicationEvent {

  private final Class<? extends Execution> executionType;
  private final String executionId;

  protected ExecutionEvent(
    @Nonnull Object source,
    @Nonnull Class<? extends Execution> executionType,
    @Nonnull String executionId
  ) {
    super(source);
    this.executionType = executionType;
    this.executionId = executionId;
  }

  public final @Nonnull Instant timestamp() {
    return Instant.ofEpochMilli(super.getTimestamp());
  }

  public final @Nonnull Class<? extends Execution> getExecutionType() {return executionType;}

  public final @Nonnull String getExecutionId() {return executionId;}
}
