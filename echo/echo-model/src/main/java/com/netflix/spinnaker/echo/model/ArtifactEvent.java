package com.netflix.spinnaker.echo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArtifactEvent {
  private Metadata details;
  private List<Artifact> artifacts;
}
