package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.longrunning.Operation;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This represents a long-running task managed by the Daemon that can be polled for status
 * information. It is made up of multiple stages, each of which have multiple events.
 */
@Data
@Slf4j
public class DaemonTask<C, T> {
  List<DaemonEvent> events = new ArrayList<>();
  List<DaemonTask> children = new ArrayList<>();
  final String name;
  final String uuid;
  boolean timedOut;
  final long timeout;
  final String version;
  State state = State.NOT_STARTED;
  DaemonResponse<T> response;
  Exception fatalError;
  List<String> runningJobs = new ArrayList<>();

  @JsonIgnore Thread runner;
  @JsonIgnore C context;
  @JsonIgnore String currentStage;

  @JsonCreator
  public DaemonTask(@JsonProperty("name") String name, @JsonProperty("timeout") long timeout) {
    this.name = name;
    this.uuid = UUID.randomUUID().toString();
    this.timeout = timeout;
    this.version =
        Optional.ofNullable(DaemonTask.class.getPackage().getImplementationVersion())
            .orElse("Unknown");
  }

  @JsonIgnore
  public Operation getLRO() {
    // TODO(brnelson): This shouldn't do this, it should set name to name and add the UUID as
    // metadata.
    return Operation.newBuilder().setName(this.uuid).setDone(this.state.isTerminal()).build();
  }

  void newStage(String name) {
    currentStage = name;
  }

  void writeMessage(String message) {
    if (currentStage == null) {
      throw new IllegalStateException(
          "Illegal attempt to write an event when no stage has started");
    }

    events.add(
        new DaemonEvent()
            .setStage(currentStage)
            .setMessage(message)
            .setTimestamp(System.currentTimeMillis()));
  }

  public void consumeTaskTree(Consumer<DaemonTask> c) {
    c.accept(this);
    children.stream().forEach((t) -> t.consumeTaskTree(c));
  }

  public enum State {
    NOT_STARTED(false),
    RUNNING(false),
    SUCCEEDED(true),
    INTERRUPTED(true),
    TIMED_OUT(true),
    FAILED(true);

    @Getter boolean terminal;

    State(boolean terminal) {
      this.terminal = terminal;
    }
  }

  void timeout() {
    timedOut = true;
    interrupt();
  }

  private boolean isInterrupted() {
    return runner.isInterrupted();
  }

  public void interrupt() {
    runner.interrupt();
  }

  void cleanupResources() {
    log.info(this + " killing all jobs created by this task " + String.join(", ", runningJobs));
    DaemonTaskHandler.getJobExecutor().cancelJobs(new ArrayList<>(runningJobs));
    SecretSessionManager.clearSession();
    for (DaemonTask child : children) {
      if (child != null) {
        log.info(this + " interrupting child " + child);

        if (timedOut) {
          child.timeout();
        } else {
          child.interrupt();
        }
      }
    }
  }

  private void inSucceededState() {
    state = State.SUCCEEDED;
  }

  private void inFailedState() {
    if (isTimedOut()) {
      state = State.TIMED_OUT;
    } else if (isInterrupted()) {
      state = State.INTERRUPTED;
    } else {
      state = State.FAILED;
    }
  }

  public void success(DaemonResponse<T> response) {
    inSucceededState();
    this.response = response;
  }

  public void failure(Exception e) {
    inFailedState();
    fatalError = e;
    Problem problem =
        new ProblemBuilder(Problem.Severity.FATAL, "Unexpected exception: " + e).build();
    response = new DaemonResponse<>(null, new ProblemSet(problem));
  }

  public void failure(HalException e) {
    inFailedState();
    fatalError = e;
    response = new DaemonResponse<>(null, e.getProblems());
  }

  <Q, P> DaemonTask<Q, P> spawnChild(
      Supplier<DaemonResponse<P>> childRunner, String name, long timeout) {
    DaemonTask child = TaskRepository.submitTask(childRunner, name, timeout);
    children.add(child);
    return child;
  }

  <P> DaemonResponse<P> reapChild(DaemonTask task) throws InterruptedException {
    DaemonTask childTask =
        children.stream()
            .filter(c -> c.getUuid().equals(task.getUuid()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Child " + task + " does not belong to this task " + this + ""));

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

    TaskRepository.getTask(childTask.getUuid());

    log.info(this + " Collected child task " + childTask + " with state " + childTask.getState());
    if (childTask.getResponse() == null) {
      throw new RuntimeException("Child response may not be null.");
    }

    return childTask.getResponse();
  }

  @Override
  public String toString() {
    return "[" + name + "] (" + uuid + ") - " + state;
  }
}
