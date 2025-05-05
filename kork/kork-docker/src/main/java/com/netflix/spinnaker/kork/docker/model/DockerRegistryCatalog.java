package com.netflix.spinnaker.kork.docker.model;

import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = false, includeFieldNames = true)
public class DockerRegistryCatalog {
  private List<String> repositories = List.of();
}
