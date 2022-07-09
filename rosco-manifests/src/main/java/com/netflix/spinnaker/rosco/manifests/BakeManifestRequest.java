package com.netflix.spinnaker.rosco.manifests;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class BakeManifestRequest {
  TemplateRenderer templateRenderer;
  String outputName;
  String outputArtifactName;
  Map<String, Object> overrides;

  public enum TemplateRenderer {
    HELM2,
    HELM3,
    KUSTOMIZE,
    KUSTOMIZE4,
    CF;

    @JsonCreator
    @Nullable
    public TemplateRenderer fromString(@Nullable String value) {
      if (value == null) {
        return null;
      }
      try {
        return TemplateRenderer.valueOf(value.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("The value '" + value + "' is not a supported renderer");
      }
    }
  }
}
