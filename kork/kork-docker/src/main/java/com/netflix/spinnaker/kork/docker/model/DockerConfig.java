package com.netflix.spinnaker.kork.docker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = false, includeFieldNames = true)
public class DockerConfig {
  @JsonProperty("mediaType")
  private String mediaType;

  @JsonProperty("digest")
  private String digest;

  @JsonProperty("size")
  private int size;
}
