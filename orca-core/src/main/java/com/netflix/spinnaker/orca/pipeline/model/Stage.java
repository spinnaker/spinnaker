/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipeline.model;

import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.support.RequisiteStageRefIdDeserializer;
import de.huxhorn.sulky.ulid.ULID;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Stage implements Serializable {

  private static final ULID ID_GENERATOR = new ULID();

  /** Sorts stages into order according to their refIds / requisiteStageRefIds. */
  public static Stream<Stage> topologicalSort(Collection<Stage> stages) {
    List<Stage> unsorted = stages.stream().filter(it -> it.parentStageId == null).collect(toList());
    ImmutableList.Builder<Stage> sorted = ImmutableList.builder();
    Set<String> refIds = new HashSet<>();
    while (!unsorted.isEmpty()) {
      List<Stage> sortable =
          unsorted.stream()
              .filter(it -> refIds.containsAll(it.getRequisiteStageRefIds()))
              .collect(toList());
      if (sortable.isEmpty()) {
        throw new IllegalStateException(
            format(
                "Invalid stage relationships found %s",
                join(
                    ", ",
                    stages.stream()
                        .map(it -> format("%s->%s", it.requisiteStageRefIds, it.refId))
                        .collect(toList()))));
      }
      sortable.forEach(
          it -> {
            unsorted.remove(it);
            refIds.add(it.refId);
            sorted.add(it);
          });
    }
    return sorted.build().stream();
  }

  public Stage() {}

  @SuppressWarnings("unchecked")
  public Stage(Execution execution, String type, String name, Map<String, Object> context) {
    this.execution = execution;
    this.type = type;
    this.name = name;
    this.context.putAll(context);

    this.refId = (String) context.remove("refId");
    this.startTimeExpiry =
        Optional.ofNullable(context.remove("startTimeExpiry"))
            .map(expiry -> Long.valueOf((String) expiry))
            .orElse(null);
    this.requisiteStageRefIds =
        Optional.ofNullable((Collection<String>) context.remove("requisiteStageRefIds"))
            .orElse(emptySet());
  }

  public Stage(Execution execution, String type, Map<String, Object> context) {
    this(execution, type, null, context);
  }

  public Stage(Execution execution, String type) {
    this(execution, type, emptyMap());
  }

  /** A stage's unique identifier */
  private String id = ID_GENERATOR.nextULID();

  public @Nonnull String getId() {
    return id;
  }

  // TODO: this shouldn't be public or used after initial construction
  public void setId(@Nonnull String id) {
    this.id = id;
  }

  private String refId;

  public @Nullable String getRefId() {
    return refId;
  }

  // TODO: this shouldn't be public or used after initial construction
  public void setRefId(@Nullable String refId) {
    this.refId = refId;
  }

  /** The type as it corresponds to the Mayo configuration */
  private String type;

  public @Nonnull String getType() {
    return type;
  }

  public void setType(@Nonnull String type) {
    this.type = type;
  }

  /** The name of the stage. Can be different from type, but often will be the same. */
  private String name;

  public @Nonnull String getName() {
    return name != null ? name : type;
  }

  public void setName(@Nonnull String name) {
    this.name = name;
  }

  /** Gets the execution object for this stage */
  private Execution execution;

  @JsonBackReference
  public @Nonnull Execution getExecution() {
    return execution;
  }

  public void setExecution(@Nonnull Execution execution) {
    this.execution = execution;
  }

  /** Gets the start time for this stage. May return null if the stage has not been started. */
  private Long startTime;

  public @Nullable Long getStartTime() {
    return startTime;
  }

  public void setStartTime(@Nullable Long startTime) {
    this.startTime = startTime;
  }

  /** Gets the end time for this stage. May return null if the stage has not yet finished. */
  private Long endTime;

  public @Nullable Long getEndTime() {
    return endTime;
  }

  public void setEndTime(@Nullable Long endTime) {
    this.endTime = endTime;
  }

  /**
   * Gets the start expiry timestamp for this stage. If the stage has not started before this
   * timestamp, the stage will be skipped.
   */
  private Long startTimeExpiry;

  public @Nullable Long getStartTimeExpiry() {
    return startTimeExpiry;
  }

  public void setStartTimeExpiry(@Nullable Long startTimeExpiry) {
    this.startTimeExpiry = startTimeExpiry;
  }

  /** The execution status for this stage */
  private ExecutionStatus status = NOT_STARTED;

  public @Nonnull ExecutionStatus getStatus() {
    return status;
  }

  public void setStatus(@Nonnull ExecutionStatus status) {
    this.status = status;
  }

  /** The context driving this stage. Provides inputs necessary to component steps */
  private Map<String, Object> context = new StageContext(this);

  public @Nonnull Map<String, Object> getContext() {
    return context;
  }

  public void setContext(@Nonnull Map<String, Object> context) {
    if (context instanceof StageContext) {
      this.context = context;
    } else {
      this.context = new StageContext(this, context);
    }
  }

  /** Outputs from this stage which may be accessed by downstream stages. */
  private Map<String, Object> outputs = new HashMap<>();

  public @Nonnull Map<String, Object> getOutputs() {
    return outputs;
  }

  public void setOutputs(@Nonnull Map<String, Object> outputs) {
    this.outputs = outputs;
  }

  /**
   * Returns the tasks that are associated with this stage. Tasks are the most granular unit of work
   * in a stage. Because tasks can be dynamically composed, this list is open updated during a
   * stage's execution.
   */
  private List<Task> tasks = new ArrayList<>();

  public @Nonnull List<Task> getTasks() {
    return tasks;
  }

  public void setTasks(@Nonnull List<Task> tasks) {
    this.tasks = new ArrayList<>(tasks);
  }

  /**
   * Stages can be synthetically injected into the pipeline by a StageDefinitionBuilder. This flag
   * indicates the relationship of a synthetic stage to its position in the graph. To derive the
   * owning stage, callers should directionally traverse the graph until the first non-synthetic
   * stage is found. If this property is null, the stage is not synthetic.
   */
  private SyntheticStageOwner syntheticStageOwner;

  public @Nullable SyntheticStageOwner getSyntheticStageOwner() {
    return syntheticStageOwner;
  }

  public void setSyntheticStageOwner(@Nullable SyntheticStageOwner syntheticStageOwner) {
    this.syntheticStageOwner = syntheticStageOwner;
  }

  /** This stage's parent stage. */
  private String parentStageId;

  public @Nullable String getParentStageId() {
    return parentStageId;
  }

  public void setParentStageId(@Nullable String parentStageId) {
    this.parentStageId = parentStageId;
  }

  @JsonDeserialize(using = RequisiteStageRefIdDeserializer.class)
  private Collection<String> requisiteStageRefIds = emptySet();

  public @Nonnull Collection<String> getRequisiteStageRefIds() {
    return ImmutableSet.copyOf(requisiteStageRefIds);
  }

  @JsonDeserialize(using = RequisiteStageRefIdDeserializer.class)
  public void setRequisiteStageRefIds(@Nonnull Collection<String> requisiteStageRefIds) {
    // This looks super weird, but when a custom deserializer is used on the method, null is passed
    // along and the
    // Nonnull check isn't triggered. Furthermore, some conditions only pick up the deserializer
    // from the setter method,
    // while others pick it up from the field. Sorry.
    if (requisiteStageRefIds == null) {
      this.requisiteStageRefIds = ImmutableSet.of();
    } else {
      this.requisiteStageRefIds = ImmutableSet.copyOf(requisiteStageRefIds);
    }
  }

  /** A date when this stage is scheduled to execute. */
  private Long scheduledTime;

  public @Nullable Long getScheduledTime() {
    return scheduledTime;
  }

  public void setScheduledTime(@Nullable Long scheduledTime) {
    this.scheduledTime = scheduledTime;
  }

  private LastModifiedDetails lastModified;

  public @Nullable LastModifiedDetails getLastModified() {
    return lastModified;
  }

  public void setLastModified(@Nullable LastModifiedDetails lastModified) {
    this.lastModified = lastModified;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Stage stage = (Stage) o;
    return Objects.equals(id, stage.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public Task taskById(String taskId) {
    return tasks.stream().filter(it -> it.getId().equals(taskId)).findFirst().orElse(null);
  }

  /**
   * Gets all ancestor stages, including the current stage.
   *
   * <p>Ancestors include: <br>
   * - stages this stage depends on (via requisiteStageRefIds) <br>
   * - parent stages of this stage <br>
   * - synthetic stages that share a parent with this stage & occur prior to current stage <br>
   */
  public List<Stage> ancestors() {
    if (execution != null) {
      Set<String> visited = Sets.newHashSetWithExpectedSize(execution.getStages().size());
      return ImmutableList.<Stage>builder()
          .add(this)
          .addAll(getAncestorsImpl(visited, false))
          .build();
    } else {
      return emptyList();
    }
  }

  /**
   * Gets all ancestor stages that are direct parents, including the current stage.
   *
   * <p>Ancestors include: <br>
   * - parent stages of this stage <br>
   * - synthetic stages that share a parent with this stage & occur prior to current stage
   */
  public List<Stage> directAncestors() {
    if (execution != null) {
      Set<String> visited = Sets.newHashSetWithExpectedSize(execution.getStages().size());
      return ImmutableList.<Stage>builder()
          .add(this)
          .addAll(getAncestorsImpl(visited, true))
          .build();
    } else {
      return emptyList();
    }
  }

  /**
   * Worker method to get the list of all ancestors (parents and, optionally, prerequisite stages)
   * of the current stage.
   *
   * @param visited list of visited nodes
   * @param directParentOnly true to only include direct parents of the stage, false to also include
   *     stages this stage depends on (via requisiteRefIds)
   * @return list of ancestor stages
   */
  private List<Stage> getAncestorsImpl(Set<String> visited, boolean directParentOnly) {
    visited.add(this.refId);

    if (!requisiteStageRefIds.isEmpty() && !directParentOnly) {
      // Get stages this stage depends on via requisiteStageRefIds:
      List<Stage> previousStages =
          execution.getStages().stream()
              .filter(it -> requisiteStageRefIds.contains(it.refId))
              .filter(it -> !visited.contains(it.refId))
              .collect(toList());
      List<Stage> syntheticStages =
          execution.getStages().stream()
              .filter(
                  s ->
                      previousStages.stream()
                          .map(Stage::getId)
                          .anyMatch(id -> id.equals(s.parentStageId)))
              .collect(toList());
      return ImmutableList.<Stage>builder()
          .addAll(previousStages)
          .addAll(syntheticStages)
          .addAll(
              previousStages.stream()
                  .flatMap(it -> it.getAncestorsImpl(visited, directParentOnly).stream())
                  .collect(toList()))
          .build();
    } else if (parentStageId != null && !visited.contains(parentStageId)) {
      // Get parent stages, but exclude already visited ones:

      List<Stage> ancestors = new ArrayList<>();
      if (getSyntheticStageOwner() == SyntheticStageOwner.STAGE_AFTER) {
        ancestors.addAll(
            execution.getStages().stream()
                .filter(
                    it ->
                        parentStageId.equals(it.parentStageId)
                            && it.getSyntheticStageOwner() == SyntheticStageOwner.STAGE_BEFORE)
                .collect(toList()));
      }

      ancestors.addAll(
          execution.getStages().stream()
              .filter(it -> it.id.equals(parentStageId))
              .findFirst()
              .<List<Stage>>map(
                  parent ->
                      ImmutableList.<Stage>builder()
                          .add(parent)
                          .addAll(parent.getAncestorsImpl(visited, directParentOnly))
                          .build())
              .orElse(emptyList()));

      return ancestors;
    } else {
      return emptyList();
    }
  }

  /**
   * Find the ancestor stage that satisfies the given predicate (including traversing the parent
   * execution graphs)
   *
   * <p>Ancestor stages include: <br>
   * - stages this stage depends on (via requisiteStageRefIds) <br>
   * - parent stages of this stage <br>
   * - synthetic stages that share a parent with this stage & occur prior to current stage <br>
   * - stages in parent execution that satisfy above criteria <br>
   *
   * @return the first stage that matches the predicate, or null
   */
  public Stage findAncestor(Predicate<Stage> predicate) {
    return Stage.findAncestor(this, this.execution, predicate);
  }

  private static Stage findAncestor(Stage stage, Execution execution, Predicate<Stage> predicate) {
    Stage matchingStage = null;

    if (stage != null && !stage.getRequisiteStageRefIds().isEmpty()) {
      List<Stage> previousStages =
          execution.getStages().stream()
              .filter(s -> stage.getRequisiteStageRefIds().contains(s.getRefId()))
              .collect(toList());

      Set<String> previousStageIds =
          new HashSet<>(previousStages.stream().map(Stage::getId).collect(toList()));
      List<Stage> syntheticStages =
          execution.getStages().stream()
              .filter(s -> previousStageIds.contains(s.getParentStageId()))
              .collect(toList());

      List<Stage> priorStages = new ArrayList<>();
      priorStages.addAll(previousStages);
      priorStages.addAll(syntheticStages);

      matchingStage = priorStages.stream().filter(predicate).findFirst().orElse(null);

      if (matchingStage == null) {
        for (Stage s : previousStages) {
          matchingStage = findAncestor(s, execution, predicate);

          if (matchingStage != null) {
            break;
          }
        }
      }
    } else if ((stage != null) && !Strings.isNullOrEmpty(stage.getParentStageId())) {
      Optional<Stage> parent =
          execution.getStages().stream()
              .filter(s -> s.getId().equals(stage.getParentStageId()))
              .findFirst();

      if (!parent.isPresent()) {
        throw new IllegalStateException(
            "Couldn't find parent of stage "
                + stage.getId()
                + " with parent "
                + stage.getParentStageId());
      }

      if (predicate.test(parent.get())) {
        matchingStage = parent.get();
      } else {
        matchingStage = findAncestor(parent.get(), execution, predicate);
      }
    } else if ((execution.getType() == PIPELINE)
        && (execution.getTrigger() instanceof PipelineTrigger)) {
      PipelineTrigger parentTrigger = (PipelineTrigger) execution.getTrigger();

      Execution parentPipelineExecution = parentTrigger.getParentExecution();
      String parentPipelineStageId = parentTrigger.getParentPipelineStageId();

      Optional<Stage> parentPipelineStage =
          parentPipelineExecution.getStages().stream()
              .filter(
                  s -> s.getType().equals("pipeline") && s.getId().equals(parentPipelineStageId))
              .findFirst();

      if (parentPipelineStage.isPresent()) {
        matchingStage = findAncestor(parentPipelineStage.get(), parentPipelineExecution, predicate);
      } else {
        List<Stage> parentPipelineStages = new ArrayList<>(parentPipelineExecution.getStages());
        parentPipelineStages.sort(
            (s1, s2) -> {
              if ((s1.endTime == null) && (s2.endTime == null)) {
                return 0;
              }

              if (s1.endTime == null) {
                return 1;
              }

              if (s2.endTime == null) {
                return -1;
              }

              return s1.endTime.compareTo(s2.endTime);
            });

        if (parentPipelineStages.size() > 0) {
          // The list is sorted in reverse order by endTime.
          matchingStage = parentPipelineStages.stream().filter(predicate).findFirst().orElse(null);

          if (matchingStage == null) {
            Stage firstStage = parentPipelineStages.get(0);

            if (predicate.test(firstStage)) {
              matchingStage = firstStage;
            } else {
              matchingStage = findAncestor(firstStage, parentPipelineExecution, predicate);
            }
          }
        } else {
          // Parent pipeline has no stages.
          matchingStage = findAncestor(null, parentPipelineExecution, predicate);
        }
      }
    }

    return matchingStage;
  }

  /** Recursively get all stages that are children of the current one */
  public List<Stage> allDownstreamStages() {
    List<Stage> children = new ArrayList<>();

    if (execution != null) {
      HashSet<String> visited = new HashSet<>();
      LinkedList<Stage> queue = new LinkedList<>();

      queue.push(this);
      boolean first = true;

      while (!queue.isEmpty()) {
        Stage stage = queue.pop();
        if (!first) {
          children.add(stage);
        }

        first = false;
        visited.add(stage.refId);

        List<Stage> childStages = stage.downstreamStages();

        childStages.stream().filter(s -> !visited.contains(s.refId)).forEach(s -> queue.add(s));
      }
    }

    return children;
  }

  /** Maps the stage's context to a typed object */
  public <O> O mapTo(Class<O> type) {
    return mapTo(null, type);
  }

  @JsonIgnore private final transient ObjectMapper objectMapper = OrcaObjectMapper.getInstance();

  /**
   * Maps the stage's context to a typed object at a provided pointer. Uses <a
   * href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a> notation for determining the
   * pointer's position
   */
  public <O> O mapTo(String pointer, Class<O> type) {
    try {
      return objectMapper.readValue(
          new TreeTraversingParser(
              getPointer(pointer != null ? pointer : "", contextToNode()), objectMapper),
          type);
    } catch (IOException e) {
      throw new IllegalArgumentException(format("Unable to map context to %s", type), e);
    }
  }

  public <O> O decodeBase64(String pointer, Class<O> type) {
    return decodeBase64(pointer, type, objectMapper);
  }

  public <O> O decodeBase64(String pointer, Class<O> type, ObjectMapper objectMapper) {
    byte[] data;
    try {
      TreeTraversingParser parser =
          new TreeTraversingParser(
              getPointer(pointer != null ? pointer : "", contextToNode()), objectMapper);
      parser.nextToken();
      data = Base64.getDecoder().decode(parser.getText());
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Value in stage context at pointer " + pointer + " is not base 64 encoded", e);
    }

    try {
      return objectMapper.readValue(data, type);
    } catch (IOException e) {
      throw new RuntimeException(
          "Could not convert " + new String(data, UTF_8) + " to " + type.getSimpleName(), e);
    }
  }

  private JsonNode getPointer(String pointer, ObjectNode rootNode) {
    return pointer != null ? rootNode.at(pointer) : rootNode;
  }

  private ObjectNode contextToNode() {
    return (ObjectNode) objectMapper.valueToTree(context);
  }

  /** Enriches stage context if it supports strategies */
  @SuppressWarnings("unchecked")
  public void resolveStrategyParams() {
    if (execution.getType() == PIPELINE) {
      Map<String, Object> parameters =
          Optional.ofNullable(execution.getTrigger())
              .map(Trigger::getParameters)
              .orElse(emptyMap());
      boolean strategy = false;
      if (parameters.get("strategy") != null) {
        strategy = (boolean) parameters.get("strategy");
      }
      if (strategy) {
        context.put("cloudProvider", parameters.get("cloudProvider"));
        context.put("cluster", parameters.get("cluster"));
        context.put("credentials", parameters.get("credentials"));
        if (parameters.get("region") != null) {
          context.put("regions", singletonList(parameters.get("region")));
        } else if (parameters.get("zone") != null) {
          context.put("zones", singletonList(parameters.get("zone")));
        }
      }
    }
  }

  /** Returns the parent of this stage or null if it is a top-level stage. */
  @JsonIgnore
  public @Nullable Stage getParent() {
    if (parentStageId == null) {
      return null;
    } else {
      return execution.stageById(parentStageId);
    }
  }

  /** Returns the top-most stage. */
  @JsonIgnore
  public Stage getTopLevelStage() {
    Stage topLevelStage = this;
    while (topLevelStage.parentStageId != null) {
      String sid = topLevelStage.parentStageId;
      Optional<Stage> stage =
          execution.getStages().stream().filter(s -> s.id.equals(sid)).findFirst();
      if (stage.isPresent()) {
        topLevelStage = stage.get();
      } else {
        throw new IllegalStateException(
            "Could not find stage by parentStageId (stage: "
                + topLevelStage.getId()
                + ", parentStageId:"
                + sid
                + ")");
      }
    }
    return topLevelStage;
  }

  @JsonIgnore
  public Optional<Stage> getParentWithTimeout() {
    Stage current = this;
    Optional<Long> timeout = Optional.empty();

    while (current != null && !timeout.isPresent()) {
      timeout = current.getTimeout();
      if (!timeout.isPresent()) {
        current = current.getParent();
      }
    }

    return timeout.isPresent() ? Optional.of(current) : Optional.empty();
  }

  @JsonIgnore
  public Optional<Long> getTimeout() {
    Object timeout = getContext().get(STAGE_TIMEOUT_OVERRIDE_KEY);
    if (timeout instanceof Integer) {
      return Optional.of((Integer) timeout).map(Integer::longValue);
    } else if (timeout instanceof Long) {
      return Optional.of((Long) timeout);
    } else if (timeout instanceof Double) {
      return Optional.of((Double) timeout).map(Double::longValue);
    }
    return Optional.empty();
  }

  public static class LastModifiedDetails implements Serializable {
    private String user;

    public @Nonnull String getUser() {
      return user;
    }

    public void setUser(@Nonnull String user) {
      this.user = user;
    }

    private Collection<String> allowedAccounts = emptySet();

    public @Nonnull Collection<String> getAllowedAccounts() {
      return ImmutableSet.copyOf(allowedAccounts);
    }

    public void setAllowedAccounts(@Nonnull Collection<String> allowedAccounts) {
      this.allowedAccounts = ImmutableSet.copyOf(allowedAccounts);
    }

    private Long lastModifiedTime;

    public @Nonnull Long getLastModifiedTime() {
      return lastModifiedTime;
    }

    public void setLastModifiedTime(@Nonnull Long lastModifiedTime) {
      this.lastModifiedTime = lastModifiedTime;
    }
  }

  @JsonIgnore
  public boolean isJoin() {
    return getRequisiteStageRefIds().size() > 1;
  }

  @JsonIgnore
  public List<Stage> downstreamStages() {
    return getExecution().getStages().stream()
        .filter(it -> it.getRequisiteStageRefIds().contains(getRefId()))
        .collect(toList());
  }

  public static final String STAGE_TIMEOUT_OVERRIDE_KEY = "stageTimeoutMs";
}
