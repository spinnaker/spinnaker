package com.netflix.spinnaker.orca.pipeline.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.listeners.StageTaskPropagationListener;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import org.codehaus.groovy.runtime.ReverseListIterator;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public interface Stage<T extends Execution<T>> {
  String getRefId();

  void setRefId(String refId);

  /**
   * A stage's unique identifier
   */
  String getId();

  /**
   * The type as it corresponds to the Mayo configuration
   */
  String getType();

  void setType(String type);

  /**
   * The name of the stage. Can be different from type, but often will be the same.
   */
  String getName();

  void setName(String name);

  /**
   * Gets the execution object for this stage
   */
  T getExecution();

  /**
   * Gets the start time for this stage. May return null if the stage has not been started.
   */
  Long getStartTime();

  /**
   * Gets the end time for this stage. May return null if the stage has not yet finished.
   */
  Long getEndTime();

  void setStartTime(Long startTime);

  void setEndTime(Long endTime);

  /**
   * The execution status for this stage
   */
  ExecutionStatus getStatus();

  /**
   * sets the execution status for this stage
   */
  void setStatus(ExecutionStatus status);

  /**
   * Gets the last stage preceding this stage that has the specified type.
   */
  default Stage preceding(String type) {
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

  /**
   * Gets all ancestor stages that satisfy {@code matcher}, including the current stage.
   */
  List<StageNavigator.Result> ancestors(BiFunction<Stage<T>, StageDefinitionBuilder, Boolean> matcher);

  /**
   * Gets all ancestor stages, including the current stage.
   */
  default List<StageNavigator.Result> ancestors() {
    return ancestors((stage, builder) -> true);
  }

  /**
   * The context driving this stage. Provides inputs necessary to component steps
   */
  Map<String, Object> getContext();

  /**
   * Returns a flag indicating if the stage is in an immutable state
   */
  default boolean isImmutable() {
    return false;
  }

  /**
   * Returns a flag indicating if the stage is a parallel initialization stage
   */
  boolean isInitializationStage();

  void setInitializationStage(boolean initializationStage);

  /**
   * @return a reference to the wrapped object in this event this object is the immutable wrapper
   */
  @JsonIgnore default Stage<T> getSelf() {
    return this;
  }

  /**
   * Returns the tasks that are associated with this stage. Tasks are the most granular unit of work in a stage.
   * Because tasks can be dynamically composed, this list is open updated during a stage's execution.
   *
   * @see StageTaskPropagationListener
   */
  List<Task> getTasks();

  /**
   * Maps the stage's context to a typed object
   */
  default <O> O mapTo(Class<O> type) {
    return mapTo(null, type);
  }

  /**
   * Maps the stage's context to a typed object at a provided pointer. Uses
   * <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a> notation for determining the pointer's position
   */
  <O> O mapTo(String pointer, Class<O> type);

  /**
   * Commits a typed object back to the stage's context. The context is recreated during this operation, so callers
   * will need to re-reference the context object to have the new values reflected
   */
  default void commit(Object obj) {
    commit("", obj);
  }

  /**
   * Commits a typed object back to the stage's context at a provided pointer. Uses <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a>
   * notation for detremining the pointer's position
   */
  void commit(String pointer, Object obj);

  /**
   * Stages can be synthetically injected into the pipeline by a StageDefinitionBuilder. This flag indicates the relationship
   * of a synthetic stage to its position in the graph. To derive the owning stage, callers should directionally
   * traverse the graph until the first non-synthetic stage is found. If this property is null, the stage is not
   * synthetic.
   */
  SyntheticStageOwner getSyntheticStageOwner();

  /**
   * @see #getSyntheticStageOwner()
   */
  void setSyntheticStageOwner(SyntheticStageOwner syntheticStageOwner);

  /**
   * This stage's parent stage.
   */
  String getParentStageId();

  /**
   * @see #getParentStageId()
   */
  void setParentStageId(String id);

  Collection<String> getRequisiteStageRefIds();

  void setRequisiteStageRefIds(Collection<String> requisiteStageRefIds);

  /**
   * @see #setScheduledTime(long scheduledTime)
   */
  long getScheduledTime();

  /**
   * Sets a date when this stage is scheduled to execute
   */
  void setScheduledTime(long scheduledTime);

  /**
   * Enriches stage context if it supports strategies
   */
  default void resolveStrategyParams() {
  }

  AbstractStage.LastModifiedDetails getLastModified();

  /**
   * @return `true` if this stage does not depend on any others to execute, i.e. it has no #requisiteStageRefIds or #parentStageId.
   */
  @JsonIgnore default boolean isInitialStage() {
    return (getRequisiteStageRefIds() == null || getRequisiteStageRefIds().isEmpty()) && getParentStageId() == null;
  }

  @JsonIgnore default boolean isJoin() {
    return getRequisiteStageRefIds() != null && getRequisiteStageRefIds().size() > 1;
  }

  @JsonIgnore default List<Stage<T>> downstreamStages() {
    return getExecution()
      .getStages()
      .stream()
      .filter(it -> it.getRequisiteStageRefIds() != null && it.getRequisiteStageRefIds().contains(getRefId()))
      .collect(toList());
  }

  String STAGE_TIMEOUT_OVERRIDE_KEY = "stageTimeoutMs";

}
