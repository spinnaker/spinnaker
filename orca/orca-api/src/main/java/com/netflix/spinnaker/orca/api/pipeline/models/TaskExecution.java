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
package com.netflix.spinnaker.orca.api.pipeline.models;

import com.netflix.spinnaker.kork.annotations.Beta;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The runtime execution state of a task. */
@Beta
public interface TaskExecution {

  @Nonnull
  String getId();

  void setId(@Nonnull String id);

  @Nonnull
  String getImplementingClass();

  void setImplementingClass(@Nonnull String implementingClass);

  @Nonnull
  String getName();

  void setName(@Nonnull String name);

  /** TODO(rz): Convert to Instant */
  @Nullable
  Long getStartTime();

  void setStartTime(@Nullable Long startTime);

  /** TODO(rz): Convert to Instant */
  @Nullable
  Long getEndTime();

  void setEndTime(@Nullable Long endTime);

  @Nonnull
  ExecutionStatus getStatus();

  void setStatus(@Nonnull ExecutionStatus status);

  boolean isStageStart();

  void setStageStart(boolean stageStart);

  boolean isStageEnd();

  void setStageEnd(boolean stageEnd);

  boolean isLoopStart();

  void setLoopStart(boolean loopStart);

  boolean isLoopEnd();

  void setLoopEnd(boolean loopEnd);

  Map<String, Object> getTaskExceptionDetails();
}
