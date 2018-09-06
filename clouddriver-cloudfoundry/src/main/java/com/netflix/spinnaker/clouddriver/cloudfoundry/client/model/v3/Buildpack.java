package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

@Data
public class Buildpack {
  private String name;
  private String detectOutput;
  private String version;
  private String buildpackName;
}
