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

import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.LOOP;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/** A node in a {@link TaskGraph} which can be either an individual task or a sub-graph. */
public interface TaskNode {

  enum GraphType {
    /** A graph representing an entire stage. */
    FULL,
    /** A graph representing a rolling-push style loop in a stage. */
    LOOP,
    /** A graph representing the pre-parallel tasks of a bake/deploy style stage */
    HEAD,
    /** A graph representing the post-parallel tasks of a bake/deploy style stage */
    TAIL
  }

  static TaskGraph build(GraphType type, Consumer<Builder> closure) {
    Builder builder = new Builder(type);
    closure.accept(builder);
    return builder.build();
  }

  static TaskGraph emptyGraph(GraphType type) {
    return build(type, builder -> {});
  }

  static TaskGraph singleton(
      GraphType type,
      String name,
      Class<? extends com.netflix.spinnaker.orca.Task> implementingClass) {
    return build(type, builder -> builder.withTask(name, implementingClass));
  }

  static TaskDefinition task(
      String name, Class<? extends com.netflix.spinnaker.orca.Task> implementingClass) {
    return new TaskDefinition(name, implementingClass);
  }

  static Builder Builder(GraphType type) {
    return new Builder(type);
  }

  class Builder {
    private final GraphType type;
    private final List<TaskNode> graph = new ArrayList<>();

    public Builder(GraphType type) {
      this.type = type;
    }

    /**
     * Adds a task to the current graph.
     *
     * @param name the name of the task.
     * @param implementingClass the class that implements the task.
     * @return this builder with the new task appended.
     */
    public Builder withTask(
        String name, Class<? extends com.netflix.spinnaker.orca.Task> implementingClass) {
      graph.add(new TaskDefinition(name, implementingClass));
      return this;
    }

    /**
     * Adds a sub-graph of tasks that may loop if any of them return {@link
     * ExecutionStatus#REDIRECT}. The sub-graph will run after any previously added tasks and before
     * any subsequently added ones. If the final task in the sub-graph returns {@link
     * ExecutionStatus#REDIRECT} the tasks in the sub-graph will be run again. If it returns {@link
     * ExecutionStatus#SUCCEEDED} the sub-graph will exit.
     *
     * @param subGraph a lambda that defines the tasks for the sub-graph by adding them to a @{link
     *     {@link TaskNode#Builder}.
     * @return this builder with the sub-graph appended.
     */
    public Builder withLoop(Consumer<Builder> subGraph) {
      Builder subGraphBuilder = new Builder(LOOP);
      subGraph.accept(subGraphBuilder);
      graph.add(subGraphBuilder.build());
      return this;
    }

    TaskGraph build() {
      return new TaskGraph(type, graph);
    }
  }

  /** A graph or sub-graph of tasks. */
  class TaskGraph implements TaskNode, Iterable<TaskNode> {

    private final GraphType type;
    private final List<TaskNode> graph;

    @VisibleForTesting
    public TaskGraph(GraphType type, List<TaskNode> graph) {
      this.type = type;
      this.graph = graph;
    }

    @Override
    public Iterator<TaskNode> iterator() {
      return graph.iterator();
    }

    public ListIterator<TaskNode> listIterator() {
      return graph.listIterator();
    }

    public boolean isEmpty() {
      return graph.isEmpty();
    }

    public GraphType getType() {
      return type;
    }
  }

  /** An individual task. */
  class TaskDefinition implements TaskNode {
    private final String name;
    private final Class<? extends com.netflix.spinnaker.orca.Task> implementingClass;

    public TaskDefinition(@Nonnull String name, @Nonnull Class<? extends Task> implementingClass) {
      this.name = name;
      this.implementingClass = implementingClass;
    }

    public @Nonnull String getName() {
      return name;
    }

    public @Nonnull Class<? extends Task> getImplementingClass() {
      return implementingClass;
    }
  }
}
