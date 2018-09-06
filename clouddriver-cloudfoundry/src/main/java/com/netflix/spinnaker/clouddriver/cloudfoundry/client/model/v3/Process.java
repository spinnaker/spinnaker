package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

@Data
public class Process {
  private String guid;
  private int instances;
  private int memoryInMb;
  private int diskInMb;
}
