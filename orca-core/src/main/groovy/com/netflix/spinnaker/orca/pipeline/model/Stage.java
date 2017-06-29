package com.netflix.spinnaker.orca.pipeline.model;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.BiFunction;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.listeners.StageTaskPropagationListener;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import lombok.Data;
import org.codehaus.groovy.runtime.ReverseListIterator;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

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
  private  String type;

  /**
   * The name of the stage. Can be different from type, but often will be the same.
   */
  private String name;

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
   * Returns a flag indicating if the stage is a parallel initialization stage
   */
  private boolean initializationStage = false;

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
   * Gets the last stage preceding this stage that has the specified type.
   */
  public Stage<T> preceding(String type) {
    int i = getExecution()
      .getStages()
      .indexOf(this);
    Iterable<Stage<T>> precedingStages = () ->
      new ReverseListIterator<>(getExecution()
        .getStages()
        .subList(0, i + 1));
    return stream(precedingStages.spliterator(), false)
      .filter(it -> it.getType().equals(type))
      .findFirst()
      .orElse(null);
  }

  public Collection<Stage<T>> children() {
    return getExecution()
      .getStages()
      .stream()
      .filter(it -> getId().equals(it.getParentStageId()))
      .collect(toList());
  }

  @JsonIgnore
  private StageNavigator stageNavigator = null;

  /**
   * Gets all ancestor stages that satisfy {@code matcher}, including the current stage.
   */
  @SuppressWarnings("unchecked")
  public List<StageNavigator.Result> ancestors(BiFunction<Stage<T>, StageDefinitionBuilder, Boolean> matcher) {
    return (List<StageNavigator.Result>) (stageNavigator != null ? stageNavigator.findAll(this, matcher) : emptyList());
  }

  /**
   * Gets all ancestor stages, including the current stage.
   */
  public List<StageNavigator.Result> ancestors() {
    return ancestors((stage, builder) -> true);
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
   * Commits a typed object back to the stage's context. The context is recreated during this operation, so callers
   * will need to re-reference the context object to have the new values reflected
   */
  public void commit(Object obj) {
    commit("", obj);
  }

  /**
   * Commits a typed object back to the stage's context at a provided pointer. Uses <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a>
   * notation for detremining the pointer's position
   */
  @SuppressWarnings("unchecked")
  public void commit(String pointer, Object obj) {
    ObjectNode rootNode = contextToNode();
    JsonNode ptr = getPointer(pointer, rootNode);
    if (ptr == null || ptr.isMissingNode()) {
      ptr = rootNode.setAll(createAndMap(pointer, obj));
    }
    mergeCommit(ptr, obj);
    context = (Map<String, Object>) objectMapper.convertValue(rootNode, LinkedHashMap.class);
  }

  private ObjectNode createAndMap(String pointer, Object obj) {
    if (!pointer.startsWith("/")) {
      throw new IllegalArgumentException("Not allowed to create a root node");
    }
    Stack<String> pathParts = new Stack<>();
    pathParts.addAll(asList(pointer.substring(1).split("/")));
    reverse(pathParts);
    ObjectNode node = objectMapper.createObjectNode();
    ObjectNode last = expand(pathParts, node);
    mergeCommit(last, obj);
    return node;
  }

  private void mergeCommit(JsonNode node, Object obj) {
    merge(objectMapper.valueToTree(obj), node);
  }

  private void merge(JsonNode sourceNode, JsonNode destNode) {
    Iterator<String> fieldNames = sourceNode.fieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      JsonNode sourceFieldValue = sourceNode.get(fieldName);
      JsonNode destFieldValue = destNode.get(fieldName);
      if (destFieldValue != null && destFieldValue.isObject()) {
        merge(sourceFieldValue, destFieldValue);
      } else if (destNode instanceof ObjectNode) {
        ((ObjectNode) destNode).replace(fieldName, sourceFieldValue);
      }
    }
  }

  private ObjectNode expand(Stack<String> path, ObjectNode node) {
    String ptr = path.pop();
    ObjectNode next = objectMapper.createObjectNode();
    node.set(ptr, next);
    return path.empty() ? next : expand(path, next);
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

  @Data
  public static class LastModifiedDetails implements Serializable {
    String user;
    Collection<String> allowedAccounts;
    Long lastModifiedTime;
  }

  /**
   * @return `true` if this stage does not depend on any others to execute, i.e. it has no #requisiteStageRefIds or #parentStageId.
   */
  @JsonIgnore public boolean isInitialStage() {
    return getRequisiteStageRefIds().isEmpty() && getParentStageId() == null;
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
