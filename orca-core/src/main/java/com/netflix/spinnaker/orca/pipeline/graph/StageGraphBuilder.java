/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.graph;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.String.format;

public class StageGraphBuilder {

  private final Stage parent;
  private final SyntheticStageOwner type;
  private final MutableGraph<Stage> graph = GraphBuilder.directed().build(); // TODO: is this actually useful?
  private final Optional<Stage> requiredPrefix;

  private StageGraphBuilder(Stage parent, SyntheticStageOwner type, Optional<Stage> requiredPrefix) {
    this.parent = parent;
    this.type = type;
    this.requiredPrefix = requiredPrefix;
    this.requiredPrefix.ifPresent(this::add);
  }

  /**
   * Create a new builder for the before stages of {@code parent}.
   */
  public static @Nonnull StageGraphBuilder beforeStages(@Nonnull Stage parent) {
    return new StageGraphBuilder(parent, STAGE_BEFORE, Optional.empty());
  }

  /**
   * Create a new builder for the before stages of {@code parent}.
   */
  public static @Nonnull StageGraphBuilder beforeStages(
    @Nonnull Stage parent, @Nullable Stage requiredPrefix) {
    return new StageGraphBuilder(parent, STAGE_BEFORE, Optional.ofNullable(requiredPrefix));
  }

  /**
   * Create a new builder for the after stages of {@code parent}.
   */
  public static @Nonnull StageGraphBuilder afterStages(@Nonnull Stage parent) {
    return new StageGraphBuilder(parent, STAGE_AFTER, Optional.empty());
  }

  /**
   * Adds a new stage to the graph. By default the new stage is not dependent on any
   * others. Use {@link #connect(Stage, Stage)} to make it depend on other stages or
   * have other stages depend on it.
   *
   * @param init builder for setting up the stage. You do not need to configure
   *             {@link Stage#execution}, {@link Stage#parentStageId},
   *             {@link Stage#syntheticStageOwner} or {@link Stage#refId} as this
   *             method will do that automatically.
   * @return the newly created stage.
   */
  public @Nonnull Stage add(@Nonnull Consumer<Stage> init) {
    Stage stage = newStage(init);
    add(stage);
    return stage;
  }

  /**
   * Adds a new stage to the graph. By default the new stage is not dependent on any
   * others. Use {@link #connect(Stage, Stage)} to make it depend on other stages or
   * have other stages depend on it.
   */
  public void add(@Nonnull Stage stage) {
    stage.setExecution(parent.getExecution());
    stage.setParentStageId(parent.getId());
    stage.setSyntheticStageOwner(type);
    if (graph.addNode(stage)) {
      stage.setRefId(generateRefId());
    }
  }

  /**
   * Adds a new stage to the graph and makes it depend on {@code previous} via its
   * {@link Stage#requisiteStageRefIds}.
   *
   * @param previous The stage the new stage will depend on. If {@code previous}
   *                 does not already exist in the graph, this method will add it.
   * @param init     See {@link #add(Consumer)}
   * @return the newly created stage.
   */
  public @Nonnull Stage connect(
    @Nonnull Stage previous,
    @Nonnull Consumer<Stage> init
  ) {
    Stage stage = add(init);
    connect(previous, stage);
    return stage;
  }

  /**
   * Makes {@code next} depend on {@code previous} via its
   * {@link Stage#requisiteStageRefIds}. If either {@code next} or
   * {@code previous} are not yet present in the graph this method will add them.
   */
  public void connect(@Nonnull Stage previous, @Nonnull Stage next) {
    add(previous);
    add(next);
    Set<String> requisiteStageRefIds = new HashSet<>(next.getRequisiteStageRefIds());
    requisiteStageRefIds.add(previous.getRefId());
    next.setRequisiteStageRefIds(requisiteStageRefIds);
    graph.putEdge(previous, next);
  }

  /**
   * Builds and returns the stages represented in the graph. This method is not
   * typically useful to implementors of {@link StageDefinitionBuilder}, it's used
   * internally and by tests.
   */
  public @Nonnull Iterable<Stage> build() {
    requiredPrefix.ifPresent(prefix ->
      graph.nodes().forEach(it -> {
        if (it != prefix && it.getRequisiteStageRefIds().isEmpty()) {
          connect(prefix, it);
        }
      }));
    return graph.nodes();
  }

  private String generateRefId() {
    return format(
      "%s%s%d",
      parent.getRefId(),
      type == STAGE_BEFORE ? "<" : ">",
      graph.nodes().size()
    );
  }

  private Stage newStage(Consumer<Stage> init) {
    Stage stage = new Stage();
    init.accept(stage);
    return stage;
  }
}
