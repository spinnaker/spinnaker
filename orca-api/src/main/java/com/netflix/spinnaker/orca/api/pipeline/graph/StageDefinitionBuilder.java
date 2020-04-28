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

import static com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.Builder;
import static com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.GraphType.FULL;

import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.TaskGraph;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nonnull;
import org.pf4j.ExtensionPoint;

/**
 * Provides a low-level API for building stages.
 *
 * <p>A stage in its simplest form will consist of a series of Tasks that are executed serially.
 * However, a stage can also configure other stages to be run before it, or after it, and or on
 * failure only. This can enable you to build stages which compose and abstract the details of other
 * stages.
 */
@Beta
public interface StageDefinitionBuilder extends ExtensionPoint {

  default @Nonnull TaskGraph buildTaskGraph(@Nonnull StageExecution stage) {
    Builder graphBuilder = Builder(FULL);
    taskGraph(stage, graphBuilder);
    return graphBuilder.build();
  }

  /**
   * Implement this method to define any tasks that should run as part of this stage's workflow.
   *
   * @param stage The execution runtime of the stage
   * @param builder The task graph builder
   */
  default void taskGraph(@Nonnull StageExecution stage, @Nonnull Builder builder) {}

  /**
   * Implement this method to define any stages that should run before any tasks in this stage as
   * part of a composed workflow.
   *
   * @param parent The execution runtime of the stage (which is the parent of any stages created
   *     herein)
   * @param graph The stage graph builder
   */
  default void beforeStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {}

  /**
   * Implement this method to define any stages that should run after any tasks in this stage as
   * part of a composed workflow.
   *
   * @param parent The execution runtime of the stage (which is the parent of any stages created
   *     herein)
   * @param graph The stage graph builder
   */
  default void afterStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {}

  /**
   * Implement this method to define any stages that should run in response to a failure in tasks,
   * before or after stages.
   *
   * @param stage The execution runtime of the stage
   * @param graph The stage graph builder
   */
  default void onFailureStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {}

  /** @return the stage type this builder handles. */
  default @Nonnull String getType() {
    return getType(this.getClass());
  }

  /**
   * Implementations can override this if they need any special cleanup on restart.
   *
   * @param stage The execution runtime of the stage
   */
  default void prepareStageForRestart(@Nonnull StageExecution stage) {}

  /**
   * Get the pipeline configuration-friendly type name for this stage.
   *
   * <p>If a stage class is {@code MyFancyStage}, the resulting type would be {@code myFancy}.
   *
   * @param clazz The stage definition builder class
   * @return
   */
  static String getType(Class<? extends StageDefinitionBuilder> clazz) {
    String className = clazz.getSimpleName();
    if (className.equals("")) {
      throw new IllegalStateException(
          "StageDefinitionBuilder.getType() cannot be called on an anonymous type");
    }
    return className.substring(0, 1).toLowerCase()
        + className
            .substring(1)
            .replaceFirst("StageDefinitionBuilder$", "")
            .replaceFirst("Stage$", "");
  }

  /** Return true if the stage can be manually skipped from the API. */
  default boolean canManuallySkip() {
    return false;
  }

  /** A collection of known aliases. */
  default Collection<String> aliases() {
    if (getClass().isAnnotationPresent(Aliases.class)) {
      return Arrays.asList(getClass().getAnnotation(Aliases.class).value());
    }

    return Collections.emptyList();
  }

  /** Allows backwards compatibility of a stage's "type", even through class renames / refactors. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface Aliases {
    String[] value() default {};
  }
}
