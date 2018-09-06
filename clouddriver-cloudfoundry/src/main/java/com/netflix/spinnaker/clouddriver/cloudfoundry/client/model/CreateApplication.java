package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ToOneRelationship;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class CreateApplication {
  private final String name;
  private final Map<String, ToOneRelationship> relationships;
  private final Map<String, String> environmentVariables;

  @Nullable
  private final BuildpackLifecycle lifecycle;

  public CreateApplication(String name, Map<String, ToOneRelationship> relationships, Map<String, String> environmentVariables,
                           @Nullable String buildpack) {
    this.name = name;
    this.relationships = relationships;
    this.environmentVariables = environmentVariables;
    this.lifecycle = buildpack != null ? new BuildpackLifecycle(buildpack) : null;
  }

  @AllArgsConstructor
  @Getter
  public static class BuildpackLifecycle {
    private String type = "buildpacks";
    private Map<String, String> data;

    public BuildpackLifecycle(String buildpack) {
      this.data = new HashMap<>();
      data.put("buildpack", buildpack);
    }
  }
}
