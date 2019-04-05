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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.*;

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
    return mapper.convertValue(input, Manifest.class).toArtifact(artifactResolver, stage);
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
}