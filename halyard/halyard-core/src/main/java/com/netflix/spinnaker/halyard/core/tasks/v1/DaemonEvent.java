package com.netflix.spinnaker.halyard.core.tasks.v1;

import java.util.Date;
import lombok.Data;

@Data
public class DaemonEvent {
  // A description of what the Daemon is doing
  String message;
  // What the larger goal of this event is. e.g. when validating a gce account, the stage could be
  // "Validating all config"
  String stage;

  Long timestamp;

  @Override
  public String toString() {
    return String.format("[%s] (%s) %s", new Date(timestamp).toString(), stage, message);
  }
}
