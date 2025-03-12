/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.api.pipeline.persistence;

import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;

/**
 * Listens for changes to {@link PipelineExecution}.
 *
 * <p>These listeners are unable to perform any changes that would affect the outcome of a
 * persistence operation. They are directly notified of persistence operations after the change has
 * been committed.
 */
@Alpha
@NonnullByDefault
public interface ExecutionRepositoryListener {

  /**
   * Listen for upsert operations.
   *
   * <p>This could be adding, updating or removing stages; canceling, pausing, resuming executions,
   * and so-on.
   */
  void onUpsert(PipelineExecution pipelineExecution);
}
