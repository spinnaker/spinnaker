/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.bakery.tasks.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.bakery.api.manifests.BakeManifestRequest;
import com.netflix.spinnaker.orca.bakery.api.manifests.helm.HelmBakeManifestRequest;
import com.netflix.spinnaker.orca.bakery.api.manifests.kustomize.KustomizeBakeManifestRequest;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateBakeManifestTask implements RetryableTask {
  @Override
  public long getBackoffPeriod() {
    return 30000;
  }

  @Override
  public long getTimeout() {
    return 300000;
  }

  @Nullable private final BakeryService bakery;

  private final ArtifactUtils artifactUtils;

  private final ContextParameterProcessor contextParameterProcessor;

  @Autowired
  public CreateBakeManifestTask(
      ArtifactUtils artifactUtils,
      ContextParameterProcessor contextParameterProcessor,
      Optional<BakeryService> bakery) {
    this.artifactUtils = artifactUtils;
    this.contextParameterProcessor = contextParameterProcessor;
    this.bakery = bakery.orElse(null);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    if (bakery == null) {
      throw new IllegalStateException(
          "A BakeryService must be configured in order to run a Bake Manifest task.");
    }

    BakeManifestContext context = stage.mapTo(BakeManifestContext.class);

    List<InputArtifact> inputArtifacts = context.getInputArtifacts();
    if (inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact to bake must be supplied");
    }

    List<Artifact> resolvedInputArtifacts =
        inputArtifacts.stream()
            .map(
                p -> {
                  Artifact a =
                      artifactUtils.getBoundArtifactForStage(stage, p.getId(), p.getArtifact());
                  if (a == null) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Input artifact (id: %s, account: %s) could not be found in execution (id: %s).",
                            p.getId(), p.getAccount(), stage.getExecution().getId()));
                  }
                  return ArtifactUtils.withAccount(a, p.getAccount());
                })
            .collect(Collectors.toList());

    List<ExpectedArtifact> expectedArtifacts = context.getExpectedArtifacts();

    if (expectedArtifacts.size() != 1) {
      throw new IllegalArgumentException(
          "The Bake (Manifest) stage produces one embedded base64 artifact.  Please ensure that your Bake (Manifest) stage"
              + " config's `Produces Artifacts` section (`expectedArtifacts` field) contains exactly one artifact.");
    }

    String outputArtifactName = expectedArtifacts.get(0).getMatchArtifact().getName();

    // TODO(ethanfrogers): encapsulate this into the HelmBakeManifestRequest
    Map<String, Object> overrides = context.getOverrides();
    Boolean evaluateOverrideExpressions = context.getEvaluateOverrideExpressions();
    if (evaluateOverrideExpressions != null && evaluateOverrideExpressions) {

      overrides =
          contextParameterProcessor.process(
              overrides, contextParameterProcessor.buildExecutionContext(stage), true);
    }

    BakeManifestRequest request;
    switch (context.getTemplateRenderer().toUpperCase()) {
      case "HELM3":
      case "HELM2":
        request =
            new HelmBakeManifestRequest(
                context, resolvedInputArtifacts, outputArtifactName, overrides);
        break;
      case "KUSTOMIZE":
      case "KUSTOMIZE4":
        Artifact inputArtifact = resolvedInputArtifacts.get(0);
        request = new KustomizeBakeManifestRequest(context, inputArtifact, outputArtifactName);
        break;
      default:
        throw new IllegalArgumentException(
            "Invalid template renderer " + context.getTemplateRenderer());
    }

    Artifact result = bakery.bakeManifest(request.getTemplateRenderer(), request);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("artifacts", Collections.singleton(result));
    outputs.put("cloudProvider", "kubernetes"); // Needed for stat collection.

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }

  @Data
  static class InputArtifact {
    String id;
    String account;
    Artifact artifact;
  }
}
