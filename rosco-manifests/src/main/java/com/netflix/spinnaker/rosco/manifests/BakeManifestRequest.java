package com.netflix.spinnaker.rosco.manifests;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.Map;
import lombok.Data;

@Data
public class BakeManifestRequest {
  TemplateRenderer templateRenderer;
  String outputName;
  String outputArtifactName;
  Map<String, Object> overrides;

  public enum TemplateRenderer {
    HELM2,
    KUSTOMIZE;

    @JsonCreator
    public TemplateRenderer fromString(String value) {
      if (value == null) {
        return null;
      }
      return Arrays.stream(values())
          .filter(v -> value.equalsIgnoreCase(v.toString()))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "The value '" + value + "' is not a supported renderer"));
    }
  }
}
