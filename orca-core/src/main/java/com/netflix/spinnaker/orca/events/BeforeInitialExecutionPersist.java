/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.events;

import com.netflix.spinnaker.orca.pipeline.model.Execution;
import org.springframework.context.ApplicationEvent;

import javax.annotation.Nonnull;

/**
 * An event emitted immediately before the initial persist of an execution.
 */
public final class BeforeInitialExecutionPersist extends ApplicationEvent {

  private final Execution execution;

  public BeforeInitialExecutionPersist(@Nonnull Object source, @Nonnull Execution execution) {
    super(source);
    this.execution = execution;
  }

  public final @Nonnull Execution getExecution() {
    return execution;
  }
}
