package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import lombok.Data;

@Data
public class InstanceStatus {
  private State state;
  private Long uptime;
  private String details;

  public enum State {
    RUNNING, STARTING, CRASHED, DOWN;
  }
}
