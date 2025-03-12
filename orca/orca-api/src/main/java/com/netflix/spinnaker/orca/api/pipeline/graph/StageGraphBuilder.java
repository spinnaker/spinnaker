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
package com.netflix.spinnaker.orca.api.pipeline.graph;

import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/** Provides a low-level API for manipulating a stage DAG. */
@Beta
public interface StageGraphBuilder {

  /**
   * Adds a new stage to the graph. By default the new stage is not dependent on any others. Use
   * {@link #connect(StageExecution, StageExecution)} to make it depend on other stages or have
   * other stages depend on it.
   *
   * @param init builder for setting up the stage. You do not need to configure {@link
   *     StageExecution#getExecution()}, {@link StageExecution#getParentStageId()}, {@link
   *     StageExecution#getSyntheticStageOwner()} or {@link StageExecution#getRefId()} as this
   *     method will do that automatically.
   * @return the newly created stage.
   */
  @Nonnull
  StageExecution add(@Nonnull Consumer<StageExecution> init);

  /**
   * Adds a new stage to the graph. By default the new stage is not dependent on any others. Use
   * {@link #connect(StageExecution, StageExecution)} to make it depend on other stages or have
   * other stages depend on it.
   */
  void add(@Nonnull StageExecution stage);

  /**
   * Adds a new stage to the graph and makes it depend on {@code previous} via its {@link
   * StageExecution#getRequisiteStageRefIds()}.
   *
   * @param previous The stage the new stage will depend on. If {@code previous} does not already
   *     exist in the graph, this method will add it.
   * @param init See {@link #add(Consumer)}
   * @return the newly created stage.
   */
  @Nonnull
  StageExecution connect(@Nonnull StageExecution previous, @Nonnull Consumer<StageExecution> init);

  /**
   * Makes {@code next} depend on {@code previous} via its {@link
   * StageExecution#getRequisiteStageRefIds()}. If either {@code next} or {@code previous} are not
   * yet present in the graph this method will add them.
   */
  void connect(@Nonnull StageExecution previous, @Nonnull StageExecution next);

  /**
   * Adds a new stage to the graph and makes it depend on the last stage that was added if any.
   *
   * <p>This is convenient for straightforward stage graphs to avoid having to pass around
   * references to stages in order to use {@link #connect(StageExecution, Consumer)}.
   *
   * <p>If no stages have been added so far, this is synonymous with calling {@link #add(Consumer)}.
   *
   * @param init See {@link #add(Consumer)}
   * @return the newly created stage.
   */
  @Nonnull
  StageExecution append(@Nonnull Consumer<StageExecution> init);

  void append(@Nonnull StageExecution stage);

  /**
   * Builds and returns the stages represented in the graph. This method is not typically useful to
   * implementors of {@link StageDefinitionBuilder}, it's used internally and by tests.
   */
  @Nonnull
  Iterable<StageExecution> build();
}
