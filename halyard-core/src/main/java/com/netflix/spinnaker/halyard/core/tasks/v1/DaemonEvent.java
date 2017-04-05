package com.netflix.spinnaker.halyard.core.tasks.v1;

import lombok.Data;

@Data
public class DaemonEvent {
  // A description of what the Daemon is doing
  String message;
  // What the larger goal of this event is. e.g. when validating a gce account, the stage could be "Validating all config"
  String stage;
  // A changing (optional) message modifying the current event.
  String detail;
  Long timestamp;
}
