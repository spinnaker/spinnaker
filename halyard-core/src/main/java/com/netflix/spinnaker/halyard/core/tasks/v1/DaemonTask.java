package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * This represents a long-running task managed by the Daemon that can be polled for status information.
 * It is made up of multiple stages, each of which have multiple events.
 */
@Data
public class DaemonTask<C, T> {
  List<DaemonStage> stages = new ArrayList<>();
  String uuid;
  State state = State.NOT_STARTED;
  DaemonResponse<T> response;
  Exception fatalError;
  @JsonIgnore C context;

  void finishStage() {
    DaemonStage lastStage = getLastStage();
    if (lastStage != null) {
      lastStage.setState(DaemonStage.State.INACTIVE);
    }
  }

  void newStage(String name) {
    finishStage();
    stages.add(new DaemonStage(name));
  }

  void writeEvent(String message) {
    DaemonStage lastStage = getLastStage();
    if (lastStage == null) {
      throw new RuntimeException("Illegal attempt to write an event when no stage has started");
    }

    stages.get(stages.size() - 1).writeEvent(message);
  }

  private DaemonStage getLastStage() {
    if (stages.isEmpty()) {
      return null;
    } else {
      return stages.get(stages.size() - 1);
    }
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
}
