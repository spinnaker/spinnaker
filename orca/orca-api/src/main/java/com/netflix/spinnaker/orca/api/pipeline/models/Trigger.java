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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.api.annotations.Immutable;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The originating cause of a pipeline's execution, including associated metadata. */
@Beta
public interface Trigger {

  @Nonnull
  String getType();

  @Nullable
  String getCorrelationId();

  @Nullable
  String getUser();

  @Immutable
  @Nonnull
  Map<String, Object> getParameters();

  @Immutable
  @Nonnull
  List<Artifact> getArtifacts();

  @Immutable
  @Nonnull
  List<Map<String, Object>> getNotifications();

  boolean isRebake();

  void setRebake(boolean rebake);

  boolean isDryRun();

  void setDryRun(boolean dryRun);

  boolean isStrategy();

  void setStrategy(boolean strategy);

  @Nonnull
  List<ExpectedArtifact> getResolvedExpectedArtifacts();

  void setResolvedExpectedArtifacts(@Nonnull List<ExpectedArtifact> resolvedExpectedArtifacts);

  /** Internal. Used for serialization/deserialization. */
  @Nonnull
  Map<String, Object> getOther();

  /** Internal. Used for serialization/deserialization. */
  void setOther(@Nonnull Map<String, Object> other);

  /** Internal. Used for serialization/deserialization. */
  void setOther(@Nonnull String key, @Nonnull Object value);
}
