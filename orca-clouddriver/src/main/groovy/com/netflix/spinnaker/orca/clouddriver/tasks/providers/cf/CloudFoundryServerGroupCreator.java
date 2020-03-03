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
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CloudFoundryServerGroupCreator implements ServerGroupCreator {
  private final ObjectMapper mapper;
  private final ArtifactUtils artifactUtils;
  private final ManifestEvaluator manifestEvaluator;

  @Override
  public List<Map> getOperations(Stage stage) {
    Map<String, Object> context = stage.getContext();

    Artifact manifestArtifact = resolveArtifact(stage, context.get("manifest"));
    CloudFoundryManifestContext manifestContext =
        CloudFoundryManifestContext.builder()
            .source(ManifestContext.Source.Artifact)
            .manifestArtifactId(manifestArtifact.getUuid())
            .manifestArtifact(manifestArtifact)
            .manifestArtifactAccount(manifestArtifact.getArtifactAccount())
            .skipExpressionEvaluation(
                (Boolean)
                    Optional.ofNullable(context.get("skipExpressionEvaluation")).orElse(false))
            .build();
    ManifestEvaluator.Result evaluatedManifest = manifestEvaluator.evaluate(stage, manifestContext);

    final Execution execution = stage.getExecution();
    ImmutableMap.Builder<String, Object> operation =
        ImmutableMap.<String, Object>builder()
            .put("application", context.get("application"))
            .put("credentials", context.get("account"))
            .put("startApplication", context.get("startApplication"))
            .put("region", context.get("region"))
            .put("executionId", execution.getId())
            .put("trigger", execution.getTrigger().getOther())
            .put("applicationArtifact", resolveArtifact(stage, context.get("applicationArtifact")))
            .put("manifest", evaluatedManifest.getManifests());

    if (context.get("stack") != null) {
      operation.put("stack", context.get("stack"));
    }

    if (context.get("freeFormDetails") != null) {
      operation.put("freeFormDetails", context.get("freeFormDetails"));
    }

    return Collections.singletonList(
        ImmutableMap.<String, Object>builder().put(OPERATION, operation.build()).build());
  }

  private Artifact resolveArtifact(Stage stage, Object input) {
    StageContextArtifactView stageContextArtifactView =
        mapper.convertValue(input, StageContextArtifactView.class);
    Artifact artifact =
        artifactUtils.getBoundArtifactForStage(
            stage,
            stageContextArtifactView.getArtifactId(),
            stageContextArtifactView.getArtifact());
    if (artifact == null) {
      throw new IllegalArgumentException("Unable to bind the application artifact");
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
  private static class StageContextArtifactView {
    @Nullable private String artifactId;
    @Nullable private Artifact artifact;
  }
}
