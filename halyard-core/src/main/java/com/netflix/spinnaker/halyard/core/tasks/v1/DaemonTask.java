package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutorLocal;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This represents a long-running task managed by the Daemon that can be polled for status information.
 * It is made up of multiple stages, each of which have multiple events.
 */
@Data
@Slf4j
public class DaemonTask<C, T> {
  List<DaemonEvent> events = new ArrayList<>();
  List<DaemonTask> children = new ArrayList<>();
  final String name;
  final String uuid;
  State state = State.NOT_STARTED;
  DaemonResponse<T> response;
  Exception fatalError;

  @JsonIgnore Thread runner;
  @JsonIgnore final JobExecutor jobExecutor;
  @JsonIgnore C context;
  @JsonIgnore String currentStage;

  @JsonCreator
  public DaemonTask(@JsonProperty("name") String name) {
    this.name = name;
    this.uuid = UUID.randomUUID().toString();
    this.jobExecutor = new JobExecutorLocal();
  }

  void newStage(String name) {
    currentStage = name;
  }

  void writeMessage(String message) {
    if (currentStage == null) {
      throw new IllegalStateException("Illegal attempt to write an event when no stage has started");
    }

    events.add(new DaemonEvent()
        .setStage(currentStage)
        .setMessage(message)
        .setTimestamp(System.currentTimeMillis())
    );
  }

  public void consumeTaskTree(Consumer<DaemonTask> c) {
    c.accept(this);
    children.stream().forEach((t) -> t.consumeTaskTree(c));
  }

  public enum State {
    NOT_STARTED,
    RUNNING,
    SUCCESS,
    INTERRUPTED,
    FATAL;

    public boolean isTerminal() {
      return this == SUCCESS || this == FATAL || this == INTERRUPTED;
    }
  }

  public void interrupt() {
    runner.interrupt();
  }

  void cleanupResources() {
    jobExecutor.cancelAllJobs();
    for (DaemonTask child : children) {
      if (child != null) {
        log.info("Interrupting child " + child);
        child.interrupt();
      }
    }
  }

  <Q, P> DaemonTask<Q, P> spawnChild(Supplier<DaemonResponse<P>> childRunner, String name) {
    DaemonTask child = TaskRepository.submitTask(childRunner, name);
    children.add(child);
    return child;
  }

  <P> DaemonResponse<P> reapChild(DaemonTask task) throws InterruptedException {
    DaemonTask childTask = children.stream()
        .filter(c -> c.getUuid().equals(task.getUuid()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Child " + task + " does not belong to this task " + this + ""));

    // Spin due to spurious wakeups
    while (!childTask.getState().isTerminal()) {
      try {
        synchronized (childTask) {
          childTask.wait();
        }
      } catch (InterruptedException e) {
        throw e;
      }
    }

    TaskRepository.collectTask(childTask.getUuid());

    log.info("Collected child task " + childTask + " with state " + childTask.getState());
    assert(childTask.getResponse() != null);

    return childTask.getResponse();
  }

  @Override
  public String toString() {
    return "[" + name + "] (" + uuid + ")";
  }
}
