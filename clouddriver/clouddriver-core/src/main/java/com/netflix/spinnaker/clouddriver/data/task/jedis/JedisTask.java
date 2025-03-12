package com.netflix.spinnaker.clouddriver.data.task.jedis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.clouddriver.data.task.SagaId;
import com.netflix.spinnaker.clouddriver.data.task.Status;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayOutput;
import com.netflix.spinnaker.clouddriver.data.task.TaskOutput;
import com.netflix.spinnaker.clouddriver.data.task.TaskState;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fields of the task are computed on-demand by querying the repository. This means that the
 * serialized task may not be internally consistent; each field will reflect the state of the task
 * in the repository at the time that field's accessor was called during serialization. This is in
 * general a difficult problem to solve with redis, which does not support atomic reads of multiple
 * keys, but has been solved in the SQL repository by fetching all data in a single query. As a
 * workaround, we'll instruct Jackson to serialize the status first. The reason is that consumers
 * tend to use the status field to check if a task is complete, and expect the other fields to be
 * filled out if it is. If there is an inconsistency between the status and other fields, we'd
 * rather return a stale value in the status field than in other fields. In general, returning an
 * older status (ie, still running) and newer other fields will just cause clients to poll again
 * until they see the updated status. Returning a newer status (ie, completed or failed) but stale
 * values in other fields will in general cause clients to use these stale values, leading to bugs.
 *
 * <p>We'll force the history to be computed next (as clients could feasibly use this to determine
 * whether a task is complete), then will not enforce an order on any other properties.
 */
@JsonPropertyOrder({"status", "history"})
public class JedisTask implements Task {

  private static final Logger log = LoggerFactory.getLogger(JedisTask.class);

  @JsonIgnore private RedisTaskRepository repository;
  private final String id;
  private final long startTimeMs;
  private String ownerId;
  private final String requestId;
  private final Set<SagaId> sagaIds;
  @JsonIgnore private final boolean previousRedis;

  public JedisTask(
      String id,
      long startTimeMs,
      RedisTaskRepository repository,
      String ownerId,
      String requestId,
      Set<SagaId> sagaIds,
      boolean previousRedis) {
    this.id = id;
    this.startTimeMs = startTimeMs;
    this.repository = repository;
    this.ownerId = ownerId;
    this.requestId = requestId;
    this.sagaIds = sagaIds;
    this.previousRedis = previousRedis;
  }

  @Override
  public void updateStatus(String phase, String status) {
    checkMutable();
    repository.addToHistory(repository.currentState(this).update(phase, status), this);
    log.info("[" + phase + "] Task: " + id + " Status: " + status);
  }

  @Override
  public void complete() {
    checkMutable();
    repository.addToHistory(repository.currentState(this).update(TaskState.COMPLETED), this);
  }

  @Deprecated
  @Override
  public void fail() {
    checkMutable();
    repository.addToHistory(repository.currentState(this).update(TaskState.FAILED), this);
  }

  @Override
  public void fail(boolean retryable) {
    checkMutable();
    repository.addToHistory(
        repository
            .currentState(this)
            .update(retryable ? TaskState.FAILED_RETRYABLE : TaskState.FAILED),
        this);
  }

  @Override
  public void addResultObjects(List<Object> results) {
    checkMutable();
    if (DefaultGroovyMethods.asBoolean(results)) {
      repository.currentState(this).ensureUpdateable();
      repository.addResultObjects(results, this);
    }
  }

  public List<Object> getResultObjects() {
    return repository.getResultObjects(this);
  }

  public List<? extends Status> getHistory() {
    List<Status> status = repository.getHistory(this);
    if (status != null && !status.isEmpty() && Iterables.getLast(status).isCompleted()) {
      return status.subList(0, status.size() - 1);
    } else {
      return status;
    }
  }

  @Override
  public String getOwnerId() {
    return ownerId;
  }

  @Override
  public Status getStatus() {
    return repository.currentState(this);
  }

  @Override
  public void addSagaId(@Nonnull SagaId sagaId) {
    this.sagaIds.add(sagaId);
  }

  @Override
  public boolean hasSagaIds() {
    return !sagaIds.isEmpty();
  }

  @Override
  public void retry() {
    checkMutable();
    repository.addToHistory(repository.currentState(this).update(TaskState.STARTED), this);
  }

  @Override
  public void updateOutput(String manifestName, String phase, String stdOut, String stdError) {
    log.info("[" + phase + "] Capturing output for Task " + id + ", manifest: " + manifestName);
    repository.addOutput(new TaskDisplayOutput(manifestName, phase, stdOut, stdError), this);
  }

  @Override
  public List<TaskOutput> getOutputs() {
    return repository.getOutputs(this);
  }

  @Override
  public void updateOwnerId(String ownerId, String phase) {
    checkMutable();
    if (ownerId == null) {
      log.debug("new owner id not provided. No update necessary.");
      return;
    }

    String previousCloudDriverHostname = this.getOwnerId().split("@")[1];
    String currentCloudDriverHostname = ownerId.split("@")[1];

    if (previousCloudDriverHostname.equals(currentCloudDriverHostname)) {
      log.debug("new owner id is the same as the previous owner Id. No update necessary.");
      return;
    }

    String previousOwnerId = this.ownerId;
    updateStatus(phase, "Re-assigning task from: " + previousOwnerId + " to: " + ownerId);
    this.ownerId = ownerId;
    repository.set(this.id, this);
    log.debug("Updated ownerId for task id={} from {} to {}", id, previousOwnerId, ownerId);
  }

  private void checkMutable() {
    if (previousRedis) {
      throw new IllegalStateException("Read-only task");
    }
  }

  public RedisTaskRepository getRepository() {
    return repository;
  }

  public void setRepository(RedisTaskRepository repository) {
    this.repository = repository;
  }

  public final String getId() {
    return id;
  }

  public final long getStartTimeMs() {
    return startTimeMs;
  }

  public final String getRequestId() {
    return requestId;
  }

  public final Set<SagaId> getSagaIds() {
    return sagaIds;
  }

  public final boolean getPreviousRedis() {
    return previousRedis;
  }

  public final boolean isPreviousRedis() {
    return previousRedis;
  }
}
