package com.netflix.spinnaker.rosco.manifests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
public class BakeManifestRequest {
  TemplateRenderer templateRenderer;
  String outputName;
  List<Artifact> inputArtifacts;
  Map<String, Object> overrides;

  public enum TemplateRenderer {
    HELM2;

    @JsonCreator
    public TemplateRenderer fromString(String value) {
      if (value == null) {
        return null;
      }

      return Arrays.stream(values())
          .filter(v -> value.equalsIgnoreCase(v.toString()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("The value '" + value + "' is not a supported renderer"));
    }
  }
}
