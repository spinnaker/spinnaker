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

package com.netflix.spinnaker.orca.pipeline;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.netflix.spinnaker.orca.pipeline.model.Execution;

import static java.lang.String.format;

public interface ExecutionRunner {
  void start(@Nonnull Execution execution) throws Exception;

  default void restart(
    @Nonnull Execution execution, @Nonnull String stageId) throws Exception {
    throw new UnsupportedOperationException();
  }

  default void reschedule(
    @Nonnull Execution execution) throws Exception {
    throw new UnsupportedOperationException();
  }

  default void unpause(
    @Nonnull Execution execution) throws Exception {
    throw new UnsupportedOperationException();
  }

  default void cancel(
    @Nonnull Execution execution,
    @Nonnull String user, @Nullable String reason) throws Exception {
    throw new UnsupportedOperationException();
  }
}
