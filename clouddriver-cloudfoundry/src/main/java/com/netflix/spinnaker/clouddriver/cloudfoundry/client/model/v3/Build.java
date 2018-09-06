package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;

@Data
public class Build {
  private String guid;
  private ZonedDateTime createdAt;
  private State state;
  @Nullable private Droplet droplet;

  public enum State {
    STAGING, STAGED, FAILED
  }
}
