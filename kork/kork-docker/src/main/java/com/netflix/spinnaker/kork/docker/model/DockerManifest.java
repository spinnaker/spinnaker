package com.netflix.spinnaker.kork.docker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = false, includeFieldNames = true)
public class DockerManifest {
  @JsonProperty("schemaVersion")
  private int schemaVersion;

  @JsonProperty("config")
  private DockerConfig config;

  @JsonProperty("layers")
  private List<DockerLayer> layers;

  @JsonProperty("annotations")
  private Map<String, String> annotations;
}
