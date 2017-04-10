package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
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
  @JsonIgnore C context;
  @JsonIgnore String currentStage;

  public DaemonTask(String name) {
    this.name = name;
    this.uuid = UUID.randomUUID().toString();
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
    FATAL;

    public boolean isTerminal() {
      return this == SUCCESS || this == FATAL;
    }
  }

  public <C, P> DaemonTask<C, P> spawnChild(Supplier<DaemonResponse<P>> childRunner, String name) {
    DaemonTask child = TaskRepository.submitTask(childRunner, name);
    children.add(child);
    return child;
  }

  public <P> DaemonResponse<P> reapChild(DaemonTask task) {
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
      } catch (InterruptedException ignored) {
      }
    }

    log.info("Collected child task " + childTask + " with state " + childTask.getState());

    return childTask.getResponse();
  }

  @Override
  public String toString() {
    return "[" + name + "] (" + uuid + ")";
  }
}
