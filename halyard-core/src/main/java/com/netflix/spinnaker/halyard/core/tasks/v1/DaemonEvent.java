package com.netflix.spinnaker.halyard.core.tasks.v1;

import lombok.Data;

@Data
public class DaemonEvent {
  String message;
  Long timestamp;
}
