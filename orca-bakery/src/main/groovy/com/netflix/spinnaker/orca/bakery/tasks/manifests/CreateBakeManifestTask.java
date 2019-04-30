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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.bakery.api.manifests.helm.HelmBakeManifestRequest;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

  @Autowired(required = false)
  BakeryService bakery;

  @Autowired
  ArtifactResolver artifactResolver;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  ContextParameterProcessor contextParameterProcessor;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    BakeManifestContext context = stage.mapTo(BakeManifestContext.class);

    List<InputArtifactPair> inputArtifactsObj = context.getInputArtifacts();
    List<Artifact> inputArtifacts;

    if (inputArtifactsObj == null || inputArtifactsObj.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact to bake must be supplied");
    }

    inputArtifacts = inputArtifactsObj.stream()
        .map(p -> {
          Artifact a = artifactResolver.getBoundArtifactForId(stage, p.getId());
          if (a == null) {
            throw new IllegalArgumentException(stage.getExecution().getId() + ": Input artifact " + p.getId() + " could not be found in the execution");
          }
          a.setArtifactAccount(p.getAccount());
          return a;
        }).collect(Collectors.toList());

    List<ExpectedArtifact> expectedArtifacts = context.getExpectedArtifacts();

    if (expectedArtifacts == null || expectedArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one expected artifact to baked manifest must be supplied");
    }

    if (expectedArtifacts.size() > 1) {
      throw new IllegalArgumentException("Too many artifacts provided as expected");
    }

    String outputArtifactName = expectedArtifacts.get(0).getMatchArtifact().getName();

    Map<String, Object> overrides = context.getOverrides();
    Boolean evaluateOverrideExpressions = context.getEvaluateOverrideExpressions();
    if (evaluateOverrideExpressions != null && evaluateOverrideExpressions) {
      overrides = contextParameterProcessor.process(
        overrides,
        contextParameterProcessor.buildExecutionContext(stage, true),
        true
      );
    }

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    request.setInputArtifacts(inputArtifacts);
    request.setTemplateRenderer(context.getTemplateRenderer());
    request.setOutputName(context.getOutputName());
    request.setOverrides(overrides);
    request.setNamespace(context.getNamespace());
    request.setOutputArtifactName(outputArtifactName);

    log.info("Requesting {}", request);
    Artifact result = bakery.bakeManifest(request);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("artifacts", Collections.singleton(result));

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }

  @Data
  protected static class InputArtifactPair {
    String id;
    String account;
  }
}
