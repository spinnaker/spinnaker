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
  List<DaemonEvent> events = new ArrayList<>();
  String uuid;
  State state = State.NOT_STARTED;
  DaemonResponse<T> response;
  Exception fatalError;
  @JsonIgnore C context;

  @JsonIgnore String currentStage;

  void newStage(String name) {
    currentStage = name;
  }

  void writeEvent(String message, String detail) {
    if (currentStage == null) {
      throw new IllegalStateException("Illegal attempt to write an event when no stage has started");
    }

    events.add(new DaemonEvent()
        .setStage(currentStage)
        .setMessage(message)
        .setDetail(detail)
        .setTimestamp(System.currentTimeMillis())
    );
  }

  void writeMessage(String message) {
    writeEvent(message, null);
  }

  void writeDetail(String detail) {
    writeEvent(null, detail);
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
