package com.netflix.spinnaker.orca.pipeline.model;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import javax.annotation.Nonnull;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.listeners.StageTaskPropagationListener;
import lombok.Data;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static java.lang.String.format;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;

@Data
public class Stage<T extends Execution<T>> implements Serializable {

  public Stage() {}

  @SuppressWarnings("unchecked")
  public Stage(T execution, String type, String name, Map<String, Object> context) {
    this.execution = execution;
    this.type = type;
    this.name = name;
    this.context.putAll(context);

    this.refId = (String) context.remove("refId");
    this.requisiteStageRefIds = Optional
      .ofNullable((Collection<String>) context.remove("requisiteStageRefIds"))
      .orElse(emptySet());
  }

  public Stage(T execution, String type, Map<String, Object> context) {
    this(execution, type, null, context);
  }

  public Stage(T execution, String type) {
    this(execution, type, emptyMap());
  }

  /**
   * A stage's unique identifier
   */
  private String id = UUID.randomUUID().toString();

  private String refId;

  /**
   * The type as it corresponds to the Mayo configuration
   */
  private String type;

  /**
   * The name of the stage. Can be different from type, but often will be the same.
   */
  private String name;

  @Nonnull
  public String getName() {
    return name != null ? name : type;
  }

  /**
   * Gets the execution object for this stage
   */
  @JsonBackReference private T execution;

  /**
   * Gets the start time for this stage. May return null if the stage has not been started.
   */
  private Long startTime;

  /**
   * Gets the end time for this stage. May return null if the stage has not yet finished.
   */
  private Long endTime;

  /**
   * The execution status for this stage
   */
  private ExecutionStatus status = NOT_STARTED;

  /**
   * The context driving this stage. Provides inputs necessary to component steps
   */
  private Map<String, Object> context = new HashMap<>();

  /**
   * Returns the tasks that are associated with this stage. Tasks are the most granular unit of work in a stage.
   * Because tasks can be dynamically composed, this list is open updated during a stage's execution.
   *
   * @see StageTaskPropagationListener
   */
  private List<Task> tasks = new ArrayList<>();

  /**
   * Stages can be synthetically injected into the pipeline by a StageDefinitionBuilder. This flag indicates the relationship
   * of a synthetic stage to its position in the graph. To derive the owning stage, callers should directionally
   * traverse the graph until the first non-synthetic stage is found. If this property is null, the stage is not
   * synthetic.
   */
  private SyntheticStageOwner syntheticStageOwner;

  /**
   * This stage's parent stage.
   */
  private String parentStageId;

  private Collection<String> requisiteStageRefIds = new HashSet<>();

  /**
   * A date when this stage is scheduled to execute.
   */
  private long scheduledTime;

  private LastModifiedDetails lastModified;

  @Override public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    Stage<?> stage = (Stage<?>) o;

    return id.equals(stage.id);
  }

  @Override public final int hashCode() {
    int result = super.hashCode();
    result = 31 * result + id.hashCode();
    return result;
  }

  public Task taskById(String taskId) {
    return tasks
      .stream()
      .filter(it -> it.getId().equals(taskId))
      .findFirst()
      .orElse(null);
  }

  /**
   * Gets all ancestor stages, including the current stage.
   */
  public List<Stage<T>> ancestors() {
    return ImmutableList
      .<Stage<T>>builder()
      .add(this)
      .addAll(ancestorsOnly())
      .build();
  }

  private List<Stage<T>> ancestorsOnly() {
    if (!requisiteStageRefIds.isEmpty()) {
      List<Stage<T>> previousStages = execution.stages.stream().filter(it ->
        requisiteStageRefIds.contains(it.refId)
      ).collect(toList());
      List<Stage<T>> syntheticStages = execution.stages.stream().filter(s ->
        previousStages.stream().map(Stage::getId).anyMatch(id -> id.equals(s.parentStageId))
      ).collect(toList());
      return ImmutableList
        .<Stage<T>>builder()
        .addAll(previousStages)
        .addAll(syntheticStages)
        .addAll(previousStages.stream().flatMap(it -> it.ancestorsOnly().stream()).collect(toList()))
        .build();
    } else if (parentStageId != null) {
      Stage<T> parent = execution.stages.stream().filter(it -> it.id.equals(parentStageId)).findFirst().orElseThrow(IllegalStateException::new);
      return ImmutableList
        .<Stage<T>>builder()
        .add(parent)
        .addAll(parent.ancestorsOnly())
        .build();
    } else {
      return emptyList();
    }
  }

  /**
   * Maps the stage's context to a typed object
   */
  public <O> O mapTo(Class<O> type) {
    return mapTo(null, type);
  }

  @JsonIgnore
  private final transient ObjectMapper objectMapper = OrcaObjectMapper.newInstance();

  /**
   * Maps the stage's context to a typed object at a provided pointer. Uses
   * <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a> notation for determining the pointer's position
   */
  public <O> O mapTo(String pointer, Class<O> type) {
    try {
      return objectMapper.readValue(new TreeTraversingParser(getPointer(pointer != null ? pointer : "", contextToNode()), objectMapper), type);
    } catch (IOException e) {
      throw new IllegalArgumentException(format("Unable to map context to %s", type), e);
    }
  }

  private JsonNode getPointer(String pointer, ObjectNode rootNode) {
    return pointer != null ? rootNode.at(pointer) : rootNode;
  }

  private ObjectNode contextToNode() {
    return (ObjectNode) objectMapper.valueToTree(context);
  }

  /**
   * Enriches stage context if it supports strategies
   */
  @SuppressWarnings("unchecked")
  public void resolveStrategyParams() {
    if (execution instanceof Pipeline) {
      Pipeline pipeline = (Pipeline) execution;
      Map<String, Object> parameters = (Map<String, Object>) pipeline.getTrigger().get("parameters");
      boolean strategy = false;
      if (parameters != null && parameters.get("strategy") != null) {
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

  /**
   * Returns the top-most stage timeout value if present.
   */
  @JsonIgnore public Optional<Long> getTopLevelTimeout() {
    Stage<T> topLevelStage = this;
    while (topLevelStage.parentStageId != null) {
      String sid = topLevelStage.parentStageId;
      Optional<Stage<T>> stage = execution.getStages().stream().filter(s -> s.id.equals(sid)).findFirst();
      if (stage.isPresent()) {
        topLevelStage = stage.get();
      } else {
        throw new IllegalStateException("Could not find stage by parentStageId (stage: " + topLevelStage.getId() + ", parentStageId:" + sid + ")");
      }
    }
    Object timeout = topLevelStage.getContext().get("stageTimeoutMs");
    if (timeout instanceof Integer) {
      return Optional.of((Integer) timeout).map(Long::new);
    } else if (timeout instanceof Long) {
      return Optional.of((Long) timeout);
    } else if (timeout instanceof Double) {
      return Optional.of((Double) timeout).map(Double::longValue);
    }
    return Optional.empty();
  }

  @Data
  public static class LastModifiedDetails implements Serializable {
    String user;
    Collection<String> allowedAccounts;
    Long lastModifiedTime;
  }

  @JsonIgnore public boolean isJoin() {
    return getRequisiteStageRefIds().size() > 1;
  }

  @JsonIgnore public List<Stage<T>> downstreamStages() {
    return getExecution()
      .getStages()
      .stream()
      .filter(it -> it.getRequisiteStageRefIds() != null && it.getRequisiteStageRefIds().contains(getRefId()))
      .collect(toList());
  }

  public static final String STAGE_TIMEOUT_OVERRIDE_KEY = "stageTimeoutMs";

}
