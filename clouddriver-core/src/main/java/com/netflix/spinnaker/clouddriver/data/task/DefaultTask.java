package com.netflix.spinnaker.clouddriver.data.task;

import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

public class DefaultTask implements Task {
  private static final Logger log = Logger.getLogger(DefaultTask.class.getName());

  private final String id;
  private String ownerId = ClouddriverHostname.ID;
  private final String requestId = null;
  private final Deque<Status> statusHistory = new ConcurrentLinkedDeque<Status>();
  private final Deque<Object> resultObjects = new ConcurrentLinkedDeque<Object>();
  private final Deque<SagaId> sagaIdentifiers = new ConcurrentLinkedDeque<SagaId>();
  private final Deque<TaskOutput> taskOutputs = new ConcurrentLinkedDeque<TaskOutput>();
  private final long startTimeMs = System.currentTimeMillis();

  public String getOwnerId() {
    return ownerId;
  }

  public DefaultTask(final String id) {
    this(id, "INIT", "Creating task " + id);
  }

  public DefaultTask(String id, String phase, String status) {
    DefaultTaskStatus initialStatus = new DefaultTaskStatus(phase, status, TaskState.STARTED);
    statusHistory.addLast(initialStatus);
    this.id = id;
  }

  public void updateStatus(String phase, String status) {
    statusHistory.addLast(currentStatus().update(phase, status));
    log.info("[" + phase + "] - Task: " + id + " " + status);
  }

  public void complete() {
    statusHistory.addLast(currentStatus().update(TaskState.COMPLETED));
  }

  public List<? extends Status> getHistory() {
    return statusHistory.stream().map(TaskDisplayStatus::new).collect(Collectors.toList());
  }

  public void fail() {
    statusHistory.addLast(currentStatus().update(TaskState.FAILED));
  }

  @Override
  public void fail(boolean retryable) {
    statusHistory.addLast(
        currentStatus().update(retryable ? TaskState.FAILED_RETRYABLE : TaskState.FAILED));
  }

  public Status getStatus() {
    return currentStatus();
  }

  public String toString() {
    return getStatus().toString();
  }

  public void addResultObjects(List<Object> results) {
    if (results != null && !results.isEmpty()) {
      currentStatus().ensureUpdateable();
      resultObjects.addAll(results);
    }
  }

  @Override
  public List<Object> getResultObjects() {
    return new ArrayList<>(resultObjects);
  }

  private DefaultTaskStatus currentStatus() {
    return (DefaultTaskStatus) statusHistory.getLast();
  }

  @Override
  public void addSagaId(@Nonnull SagaId sagaId) {
    sagaIdentifiers.addLast(sagaId);
  }

  @Override
  public Set<SagaId> getSagaIds() {
    return DefaultGroovyMethods.toSet(sagaIdentifiers);
  }

  @Override
  public boolean hasSagaIds() {
    return !sagaIdentifiers.isEmpty();
  }

  @Override
  public void retry() {
    statusHistory.addLast(currentStatus().update(TaskState.STARTED));
  }

  @Override
  public void updateOutput(String manifest, String phase, String stdOut, String stdError) {
    log.info("[" + phase + "] - Capturing output for Task: " + id + ", manifest: " + manifest);
    TaskDisplayOutput output = new TaskDisplayOutput(manifest, phase, stdOut, stdError);
    taskOutputs.addLast(output);
  }

  @Override
  public List<TaskOutput> getOutputs() {
    return new ArrayList<>(taskOutputs);
  }

  @Override
  public void updateOwnerId(String ownerId, String phase) {
    if (ownerId == null) {
      log.info("new owner id not provided. No update necessary.");
      return;
    }

    String previousCloudDriverHostname = this.getOwnerId().split("@")[1];
    String currentCloudDriverHostname = ownerId.split("@")[1];

    if (previousCloudDriverHostname.equals(currentCloudDriverHostname)) {
      log.info("new owner id is the same as the previous owner Id. No update necessary.");
      return;
    }

    String previousOwnerId = this.ownerId;
    updateStatus(phase, "Re-assigning task from: " + previousOwnerId + " to: " + ownerId);
    this.ownerId = ownerId;
    log.info(
        "Updated ownerId for task id: " + id + " from: " + previousOwnerId + " to: " + ownerId);
  }

  public final String getId() {
    return id;
  }

  public final String getRequestId() {
    return requestId;
  }

  public final long getStartTimeMs() {
    return startTimeMs;
  }
}
