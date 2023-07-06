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
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
@Getter
public class DeploymentManifest {
  @Nullable private Direct direct;

  @Nullable private String artifactId;

  @Nullable private Artifact artifact;

  public Artifact getArtifact() {
    if (direct != null) {
      return Artifact.builder()
          .name("manifest")
          .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
          .artifactAccount("embedded-artifact")
          .reference(Base64.getEncoder().encodeToString(direct.toManifestYml().getBytes()))
          .build();
    }
    return this.artifact;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class Direct extends DirectManifest {
    private final String name = "app"; // doesn't matter, has no effect on the CF app name.

    private List<String> buildpacks;

    @JsonProperty("disk_quota")
    @JsonAlias("diskQuota")
    private String diskQuota;

    private String healthCheckType;

    private String healthCheckHttpEndpoint;

    private Map<String, String> env;

    public void setEnvironment(List<EnvironmentVariable> environment) {
      this.env =
          environment.stream()
              .collect(
                  Collectors.toMap(EnvironmentVariable::getKey, EnvironmentVariable::getValue));
    }

    private List<String> routes;

    public List<Map<String, String>> getRoutes() {
      return routes.stream()
          .map(r -> ImmutableMap.<String, String>builder().put("route", r).build())
          .collect(Collectors.toList());
    }

    private List<String> services;

    private Integer instances;

    private String memory;

    @Override
    String toManifestYml() {
      try {
        Map<String, List<Direct>> apps =
            ImmutableMap.<String, List<Direct>>builder()
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
