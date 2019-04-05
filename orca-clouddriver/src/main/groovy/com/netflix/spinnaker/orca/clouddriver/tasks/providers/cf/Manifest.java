/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Setter
public class Manifest {
  @Nullable
  private Direct direct;

  @Nullable
  private String artifactId;

  @Nullable
  private Artifact artifact;

  public Artifact toArtifact(ArtifactResolver artifactResolver, Stage stage) {
    if (direct != null) {
      return Artifact.builder()
        .name("manifest")
        .type("embedded/base64")
        .artifactAccount("embedded-artifact")
        .reference(Base64.getEncoder().encodeToString(direct.toManifestYml().getBytes()))
        .build();
    }

    Artifact boundArtifact = artifactResolver.getBoundArtifactForStage(stage, artifactId, this.artifact);
    if(boundArtifact == null) {
      throw new IllegalArgumentException("Unable to bind the manifest artifact");
    }
    return boundArtifact;
  }

  public static class Direct {
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
        Map<String, List<Direct>> apps = ImmutableMap.<String, List<Direct>>builder()
          .put("applications", Collections.singletonList(this))
          .build();
        return manifestMapper.writeValueAsString(apps);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Unable to generate Cloud Foundry Manifest", e);
      }
    }
  }

  @Data
  public static class EnvironmentVariable {
    private String key;
    private String value;
  }
}
