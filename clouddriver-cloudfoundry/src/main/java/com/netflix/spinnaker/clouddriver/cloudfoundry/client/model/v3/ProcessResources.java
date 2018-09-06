package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;

import java.util.List;

@Data
public class ProcessResources {
  private List<ProcessStats> resources;
}
