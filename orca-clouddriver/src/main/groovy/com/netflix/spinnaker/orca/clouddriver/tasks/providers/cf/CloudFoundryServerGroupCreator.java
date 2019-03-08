/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
class CloudFoundryServerGroupCreator implements ServerGroupCreator {
  private final ObjectMapper mapper;
  private final ArtifactResolver artifactResolver;

  CloudFoundryServerGroupCreator(ObjectMapper mapper, ArtifactResolver artifactResolver) {
    this.mapper = mapper;
    this.artifactResolver = artifactResolver;
  }

  @Override
  public List<Map> getOperations(Stage stage) {
    Map<String, Object> context = stage.getContext();
    ImmutableMap.Builder<String, Object> operation = ImmutableMap.<String, Object>builder()
      .put("application", context.get("application"))
      .put("credentials", context.get("account"))
      .put("startApplication", context.get("startApplication"))
      .put("region", context.get("region"))
      .put("applicationArtifact", applicationArtifact(stage, context.get("applicationArtifact")))
      .put("manifest", manifestArtifact(stage, context.get("manifest")));

    if (context.get("stack") != null) {
      operation.put("stack", context.get("stack"));
    }

    if (context.get("freeFormDetails") != null) {
      operation.put("freeFormDetails", context.get("freeFormDetails"));
    }

    return Collections.singletonList(ImmutableMap.<String, Object>builder()
      .put(OPERATION, operation.build())
      .build());
  }

  private Artifact applicationArtifact(Stage stage, Object input) {
    ApplicationArtifact applicationArtifactInput = mapper.convertValue(input, ApplicationArtifact.class);
    Artifact artifact = artifactResolver.getBoundArtifactForStage(stage, applicationArtifactInput.getArtifactId(),
      applicationArtifactInput.getArtifact());
    if(artifact == null) {
      throw new IllegalArgumentException("Unable to bind the application artifact");
    }

    return artifact;
  }

  private Artifact manifestArtifact(Stage stage, Object input) {
    Manifest manifest = mapper.convertValue(input, Manifest.class);
    if (manifest.getDirect() != null) {
      return Artifact.builder()
        .name("manifest")
        .type("embedded/base64")
        .artifactAccount("embedded-artifact")
        .reference(Base64.getEncoder().encodeToString(manifest.getDirect().toManifestYml().getBytes()))
        .build();
    }

    Artifact artifact = artifactResolver.getBoundArtifactForStage(stage, manifest.getArtifactId(), manifest.getArtifact());
    if(artifact == null) {
      throw new IllegalArgumentException("Unable to bind the manifest artifact");
    }

    return artifact;
  }

  @Override
  public boolean isKatoResultExpected() {
    return false;
  }

  @Override
  public String getCloudProvider() {
    return "cloudfoundry";
  }

  @Override
  public Optional<String> getHealthProviderName() {
    return Optional.empty();
  }

  @Data
  private static class ApplicationArtifact {
    @Nullable
    private String artifactId;

    @Nullable
    private Artifact artifact;
  }

  @Data
  static class Manifest {
    @Nullable
    private DirectManifest direct;

    @Nullable
    private String artifactId;

    @Nullable
    private Artifact artifact;
  }

  static class DirectManifest {
    private static ObjectMapper manifestMapper = new ObjectMapper(new YAMLFactory()
      .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
      .enable(YAMLGenerator.Feature.INDENT_ARRAYS))
      .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);

    @Getter
    private final String name = "app"; // doesn't matter, has no effect on the CF app name.

    @Getter
    @Setter
    private List<String> buildpacks;

    @Getter
    @Setter
    @JsonProperty("disk_quota")
    @JsonAlias("diskQuota")
    private String diskQuota;

    @Getter
    @Setter
    private String healthCheckType;

    @Getter
    @Setter
    private String healthCheckHttpEndpoint;

    @Getter
    private Map<String, String> env;

    public void setEnvironment(List<EnvironmentVariable> environment) {
      this.env = environment.stream().collect(Collectors.toMap(EnvironmentVariable::getKey, EnvironmentVariable::getValue));
    }

    @Setter
    private List<String> routes;

    public List<Map<String, String>> getRoutes() {
      return routes.stream()
        .map(r -> ImmutableMap.<String, String>builder().put("route", r).build())
        .collect(Collectors.toList());
    }

    @Getter
    @Setter
    private List<String> services;

    @Getter
    @Setter
    private Integer instances;

    @Getter
    @Setter
    private String memory;

    String toManifestYml() {
      try {
        Map<String, List<DirectManifest>> apps = ImmutableMap.<String, List<DirectManifest>>builder()
          .put("applications", Collections.singletonList(this))
          .build();
        return manifestMapper.writeValueAsString(apps);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Unable to generate Cloud Foundry Manifest", e);
      }
    }
  }

  @Data
  static class EnvironmentVariable {
    String key;
    String value;
  }
}