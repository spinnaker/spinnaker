package com.netflix.spinnaker.kork.docker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = false, includeFieldNames = true)
public class DockerRegistryTags {
  @JsonProperty("name")
  private String name;

  @JsonProperty("tags")
  private List<String> tags;
}
