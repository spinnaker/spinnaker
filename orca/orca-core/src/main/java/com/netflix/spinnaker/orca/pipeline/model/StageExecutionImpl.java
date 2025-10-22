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

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
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
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner;
import com.netflix.spinnaker.orca.api.pipeline.models.*;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.support.RequisiteStageRefIdDeserializer;
import de.huxhorn.sulky.ulid.ULID;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class StageExecutionImpl implements StageExecution, Serializable {

  private static final ULID ID_GENERATOR = new ULID();

  /**
   * Sorts stages into order according to their refIds / requisiteStageRefIds and returns the result
   * as an ImmutableList. This method does not exclude synthetic stages, i.e., stages having a
   * non-null parent.
   */
  public static ImmutableList<StageExecution> topologicalSortAsImmutableListAllStages(
      Collection<StageExecution> stages) {
    return topologicalSort(stages, stage -> true);
  }

  /**
   * Sorts stages into order according to their refIds / requisiteStageRefIds and returns the result
   * as an ImmutableList.This excludes synthetic stages.
   */
  public static ImmutableList<StageExecution> topologicalSortAsImmutableList(
      Collection<StageExecution> stages) {
    return topologicalSort(stages, it -> it.getParentStageId() == null);
  }

  /**
   * Sorts the given stages into a topological order according to their refIds /
   * requisiteStageRefIds and returns the result as an ImmutableList.
   *
   * @param stages the collection of StageExecution objects to be sorted
   * @param stageFilter a predicate to filter the initial stages for sorting
   * @return an ImmutableList of StageExecution objects sorted in topological order
   * @throws IllegalStateException if there are invalid stage relationships
   */
  private static ImmutableList<StageExecution> topologicalSort(
      Collection<StageExecution> stages, Predicate<StageExecution> stageFilter) {
    List<StageExecution> unsorted =
        stages.stream().filter(stageFilter).collect(Collectors.toList());
    ImmutableList.Builder<StageExecution> sorted = ImmutableList.builder();
    Set<String> refIds = new HashSet<>();
    while (!unsorted.isEmpty()) {
      List<StageExecution> sortable =
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
                        .map(it -> format("%s->%s", it.getRequisiteStageRefIds(), it.getRefId()))
                        .collect(toList()))));
      }
      sortable.forEach(
          it -> {
            unsorted.remove(it);
            refIds.add(it.getRefId());
            sorted.add(it);
          });
    }
    return sorted.build();
  }

  /** Sorts stages into order according to their refIds / requisiteStageRefIds. */
  public static Stream<StageExecution> topologicalSort(Collection<StageExecution> stages) {
    return topologicalSortAsImmutableList(stages).stream();
  }

  public StageExecutionImpl() {}

  @SuppressWarnings("unchecked")
  public StageExecutionImpl(
      PipelineExecution execution, String type, String name, Map<String, Object> context) {
    this.execution = execution;
    this.type = type;
    this.name = name;

    this.refId = (String) context.remove("refId");
    this.startTimeExpiry =
        Optional.ofNullable(context.remove("startTimeExpiry"))
            .map(expiry -> Long.valueOf((String) expiry))
            .orElse(null);
    this.requisiteStageRefIds =
        Optional.ofNullable((Collection<String>) context.remove("requisiteStageRefIds"))
            .orElse(emptySet());

    this.context.putAll(context);
  }

  public StageExecutionImpl(PipelineExecution execution, String type, Map<String, Object> context) {
    this(execution, type, null, context);
  }

  public StageExecutionImpl(PipelineExecution execution, String type) {
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
  private PipelineExecution execution;

  @JsonBackReference
  public @Nonnull PipelineExecution getExecution() {
    return execution;
  }

  @Override
  public void setExecution(@Nonnull PipelineExecution execution) {
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
  private List<TaskExecution> tasks = new ArrayList<>();

  public @Nonnull List<TaskExecution> getTasks() {
    return tasks;
  }

  public void setTasks(@Nonnull List<TaskExecution> tasks) {
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

  @JsonIgnore private Long size = null;

  @Override
  public Optional<Long> getSize() {
    return Optional.ofNullable(this.size);
  }

  @Override
  public void setSize(long size) {
    this.size = size;
  }

  @Nullable
  @Override
  public StageExecution.LastModifiedDetails getLastModified() {
    return lastModified;
  }

  public void setLastModified(@Nullable LastModifiedDetails lastModified) {
    if (lastModified != null
        && this.lastModified != null
        && lastModified.getLastModifiedTime() < this.lastModified.getLastModifiedTime()) {
      log.warn(
          "Setting lastModified to a value with an older timestamp, current={}, new={}",
          this.lastModified,
          lastModified);
    }

    this.lastModified = lastModified;
  }

  /**
   * Additional tags to be used with stage metrics. This is useful to add extra dimensions to the
   * metrics recorded for built-in or custom stages.
   */
  private Map<String, String> additionalMetricTags;

  @Nullable
  @Override
  public Map<String, String> getAdditionalMetricTags() {
    return this.additionalMetricTags;
  }

  @Override
  public void setAdditionalMetricTags(Map<String, String> additionalMetricTags) {
    this.additionalMetricTags = additionalMetricTags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StageExecutionImpl stage = (StageExecutionImpl) o;
    return Objects.equals(id, stage.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public TaskExecution taskById(@Nonnull String taskId) {
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
  @Nonnull
  public List<StageExecution> ancestors() {
    if (execution != null) {
      Set<String> visited = Sets.newHashSetWithExpectedSize(execution.getStages().size());
      return ImmutableList.<StageExecution>builder()
          .add(this)
          .addAll(StageExecutionInternals.getAncestorsImpl(this, visited, false))
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
  @Nonnull
  public List<StageExecution> directAncestors() {
    if (execution != null) {
      Set<String> visited = Sets.newHashSetWithExpectedSize(execution.getStages().size());
      return ImmutableList.<StageExecution>builder()
          .add(this)
          .addAll(StageExecutionInternals.getAncestorsImpl(this, visited, true))
          .build();
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
  public StageExecution findAncestor(Predicate<StageExecution> predicate) {
    return findAncestor(this, this.execution, predicate);
  }

  private static StageExecution findAncestor(
      StageExecution stage, PipelineExecution execution, Predicate<StageExecution> predicate) {
    StageExecution matchingStage = null;

    if (stage != null && !stage.getRequisiteStageRefIds().isEmpty()) {
      List<StageExecution> previousStages =
          execution.getStages().stream()
              .filter(s -> stage.getRequisiteStageRefIds().contains(s.getRefId()))
              .collect(toList());

      Set<String> previousStageIds =
          new HashSet<>(previousStages.stream().map(StageExecution::getId).collect(toList()));
      List<StageExecution> syntheticStages =
          execution.getStages().stream()
              .filter(s -> previousStageIds.contains(s.getParentStageId()))
              .collect(toList());

      List<StageExecution> priorStages = new ArrayList<>();
      priorStages.addAll(previousStages);
      priorStages.addAll(syntheticStages);

      matchingStage = priorStages.stream().filter(predicate).findFirst().orElse(null);

      if (matchingStage == null) {
        for (StageExecution s : previousStages) {
          matchingStage = findAncestor(s, execution, predicate);

          if (matchingStage != null) {
            break;
          }
        }
      }
    } else if ((stage != null) && !Strings.isNullOrEmpty(stage.getParentStageId())) {
      Optional<StageExecution> parent =
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

      PipelineExecution parentPipelineExecution = parentTrigger.getParentExecution();
      String parentPipelineStageId = parentTrigger.getParentPipelineStageId();

      Optional<StageExecution> parentPipelineStage =
          parentPipelineExecution.getStages().stream()
              .filter(
                  s -> s.getType().equals("pipeline") && s.getId().equals(parentPipelineStageId))
              .findFirst();

      if (parentPipelineStage.isPresent()) {
        matchingStage = findAncestor(parentPipelineStage.get(), parentPipelineExecution, predicate);
      } else {
        List<StageExecution> parentPipelineStages =
            parentPipelineExecution.getStages().stream()
                .sorted(
                    (s1, s2) -> {
                      if ((s1.getEndTime() == null) && (s2.getEndTime() == null)) {
                        return 0;
                      }

                      if (s1.getEndTime() == null) {
                        return -1;
                      }

                      if (s2.getEndTime() == null) {
                        return 1;
                      }

                      return s2.getEndTime().compareTo(s1.getEndTime());
                    })
                .collect(toList());

        if (parentPipelineStages.size() > 0) {
          // The list is sorted in reverse order by endTime.
          matchingStage = parentPipelineStages.stream().filter(predicate).findFirst().orElse(null);

          if (matchingStage == null) {
            StageExecution firstStage = parentPipelineStages.get(0);

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
  @Nonnull
  public List<StageExecution> allDownstreamStages() {
    List<StageExecution> children = new ArrayList<>();

    if (execution != null) {
      List<StageExecution> notVisited = new ArrayList<>(getExecution().getStages());
      LinkedList<StageExecution> queue = new LinkedList<>();

      queue.push(this);
      boolean first = true;

      while (!queue.isEmpty()) {
        StageExecution stage = queue.pop();
        if (!first) {
          children.add(stage);
        }

        first = false;
        notVisited.remove(stage);

        notVisited.stream()
            .filter(
                s -> s.getRequisiteStageRefIds().contains(stage.getRefId()) && !queue.contains(s))
            .forEach(queue::add);
      }
    }

    return children;
  }

  /**
   * Gets all direct children of the current stage. This is not a recursive method and will return
   * only the children in the first level of the stage.
   */
  @Nonnull
  public List<StageExecution> directChildren() {
    if (execution != null) {
      return getExecution().getStages().stream()
          .filter(
              stage -> stage.getParentStageId() != null && stage.getParentStageId().equals(getId()))
          .collect(toList());
    }
    return emptyList();
  }

  /** Maps the stage's context to a typed object */
  @Nonnull
  public <O> O mapTo(@Nonnull Class<O> type) {
    return mapTo(null, type);
  }

  @JsonIgnore private final transient ObjectMapper objectMapper = OrcaObjectMapper.getInstance();

  /**
   * Maps the stage's context to a typed object at a provided pointer. Uses <a
   * href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a> notation for determining the
   * pointer's position
   */
  @Nonnull
  public <O> O mapTo(@Nullable String pointer, @Nonnull Class<O> type) {
    try {
      return objectMapper.readValue(
          new TreeTraversingParser(
              getPointer(pointer != null ? pointer : "", contextToNode()), objectMapper),
          type);
    } catch (IOException e) {
      throw new IllegalArgumentException(format("Unable to map context to %s", type), e);
    }
  }

  @Nonnull
  public <O> O decodeBase64(@Nullable String pointer, @Nonnull Class<O> type) {
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

  public void appendErrorMessage(String errorMessage) {
    Map<String, Object> exception =
        (Map<String, Object>) getContext().getOrDefault("exception", new HashMap<String, Object>());

    Map<String, Object> exceptionDetails =
        (Map<String, Object>) exception.getOrDefault("details", new HashMap<String, Object>());
    exception.putIfAbsent("details", exceptionDetails);
    List<String> errors =
        (List<String>) exceptionDetails.getOrDefault("errors", new ArrayList<String>());
    exceptionDetails.putIfAbsent("errors", errors);

    // Path: exception.details.errors
    errors.add(errorMessage);

    // This might be a no-op, but if there wasn't an exception object there, we should add it to the
    // context
    getContext().put("exception", exception);
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
  public @Nullable StageExecution getParent() {
    if (parentStageId == null) {
      return null;
    } else {
      return execution.stageById(parentStageId);
    }
  }

  /** Returns the top-most stage. */
  @Nonnull
  @JsonIgnore
  public StageExecution getTopLevelStage() {
    StageExecution topLevelStage = this;
    while (topLevelStage.getParentStageId() != null) {
      String sid = topLevelStage.getParentStageId();
      Optional<StageExecution> stage =
          execution.getStages().stream().filter(s -> s.getId().equals(sid)).findFirst();
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

  @Nonnull
  @JsonIgnore
  public Optional<StageExecution> getParentWithTimeout() {
    StageExecution current = this;
    Optional<Long> timeout = Optional.empty();

    while (current != null && !timeout.isPresent()) {
      timeout = current.getTimeout();
      if (!timeout.isPresent()) {
        current = current.getParent();
      }
    }

    return timeout.isPresent() ? Optional.of(current) : Optional.empty();
  }

  @Nonnull
  @JsonIgnore
  private Optional<Long> getLongFromContext(String key) {
    Object value = getContext().get(key);
    if (value instanceof Number) {
      return Optional.of(((Number) value).longValue());
    }
    return Optional.empty();
  }

  @Nonnull
  @JsonIgnore
  public Optional<Long> getTimeout() {
    return getLongFromContext(STAGE_TIMEOUT_OVERRIDE_KEY);
  }

  @Nonnull
  @JsonIgnore
  public Optional<Long> getBackoffPeriod() {
    return getLongFromContext(STAGE_BACKOFF_PERIOD_OVERRIDE_KEY);
  }

  /**
   * Check if this stage should propagate FAILED_CONTINUE to parent stage. Normally, if a synthetic
   * child fails with FAILED_CONTINUE {@link
   * com.netflix.spinnaker.orca.q.handler.CompleteStageHandler} will propagate the FAILED_CONTINUE
   * status to the parent, preventing all subsequent sibling stages from executing. This allows for
   * an option (similar to Tasks) to continue execution if a child stage returns FAILED_CONTINUE
   *
   * @return true if we want to allow subsequent siblings to continue even if this stage returns
   *     FAILED_CONTINUE
   */
  @JsonIgnore
  public boolean getAllowSiblingStagesToContinueOnFailure() {
    if (parentStageId == null) {
      return false;
    }

    StageContext context = (StageContext) getContext();
    return (boolean) context.getCurrentOnly("allowSiblingStagesToContinueOnFailure", false);
  }

  @JsonIgnore
  public void setAllowSiblingStagesToContinueOnFailure(boolean propagateFailuresToParent) {
    if (parentStageId == null) {
      throw new SpinnakerException(
          String.format(
              "Not allowed to set propagateFailuresToParent on a non-child stage: %s with id %s",
              getType(), getId()));
    }

    context.put("allowSiblingStagesToContinueOnFailure", propagateFailuresToParent);
  }

  @JsonIgnore
  public void setContinuePipelineOnFailure(boolean continuePipeline) {
    context.put("continuePipeline", continuePipeline);
  }

  @JsonIgnore
  public boolean getContinuePipelineOnFailure() {
    StageContext context = (StageContext) getContext();
    return (boolean) context.getCurrentOnly("continuePipeline", false);
  }

  @JsonIgnore
  public boolean isJoin() {
    return getRequisiteStageRefIds().size() > 1;
  }

  @JsonIgnore
  @Override
  public boolean isManualJudgmentType() {
    return Objects.equals(this.type, "manualJudgment");
  }

  @Override
  public boolean withPropagateAuthentication() {
    return context.get("propagateAuthenticationContext") != null
        && Boolean.parseBoolean(context.get("propagateAuthenticationContext").toString());
  }

  @Nonnull
  @JsonIgnore
  public List<StageExecution> downstreamStages() {
    return getExecution().getStages().stream()
        .filter(it -> it.getRequisiteStageRefIds().contains(getRefId()))
        .collect(toList());
  }

  @Override
  public String toString() {
    return "Stage {id='" + id + "', executionId='" + execution.getId() + "'}";
  }

  /**
   * NOTE: this function is mostly for convenience to endusers using SpEL
   *
   * @return true if stage has succeeded
   */
  @JsonIgnore
  public boolean getHasSucceeded() {
    return (status == ExecutionStatus.SUCCEEDED);
  }

  /**
   * NOTE: this function is mostly for convenience to endusers using SpEL
   *
   * @return true if stage has failed
   */
  @JsonIgnore
  public boolean getHasFailed() {
    return (status == ExecutionStatus.TERMINAL);
  }

  public static final String STAGE_BACKOFF_PERIOD_OVERRIDE_KEY = "backoffPeriodMs";
  public static final String STAGE_TIMEOUT_OVERRIDE_KEY = "stageTimeoutMs";
}
