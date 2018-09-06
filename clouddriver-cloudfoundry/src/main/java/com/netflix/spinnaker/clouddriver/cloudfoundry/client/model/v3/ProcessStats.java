package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

@Data
public class ProcessStats {
  private State state;

  public enum State {
    RUNNING, CRASHED, STARTING, DOWN
  }
}
