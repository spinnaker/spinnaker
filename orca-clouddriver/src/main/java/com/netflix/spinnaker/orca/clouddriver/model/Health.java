package com.netflix.spinnaker.orca.clouddriver.model;

import lombok.Data;

@Data
public class Health {
  public String type;
  public HealthState state;
  public String healthClass;
  public String healthCheckUrl; // TODO: is this real? I don't see it in CloudDriver
  public String
      status; // TODO: is this real or a bug in OortHelper? The path that calls it seems unused
}
