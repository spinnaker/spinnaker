package com.netflix.spinnaker.halyard.core.tasks.v1;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * This represents a long-running task managed by the Daemon that can be polled for status information.
 */
@Data
public class DaemonTask<T> {
  List<DaemonEvent> events = new ArrayList<>();

  public DaemonTask writeEvent(String message) {
    DaemonEvent event = new DaemonEvent()
        .setMessage(message)
        .setTimestamp(System.currentTimeMillis());

    events.add(event);
    return this;
  }

  String uuid;
  State state = State.NOT_STARTED;
  DaemonResponse<T> response;

  public enum State {
    NOT_STARTED,
    RUNNING,
    COMPLETED
  }
}
