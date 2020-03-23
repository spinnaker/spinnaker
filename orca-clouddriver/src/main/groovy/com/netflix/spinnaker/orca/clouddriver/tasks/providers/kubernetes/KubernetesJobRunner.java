/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.JobRunner;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext.Source;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.RunJobManifestContext;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.*;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class KubernetesJobRunner implements JobRunner {

  private boolean katoResultExpected = false;
  private String cloudProvider = "kubernetes";

  private ArtifactUtils artifactUtils;
  private ObjectMapper objectMapper;
  private ManifestEvaluator manifestEvaluator;

  public KubernetesJobRunner(
      ArtifactUtils artifactUtils, ObjectMapper objectMapper, ManifestEvaluator manifestEvaluator) {
    this.artifactUtils = artifactUtils;
    this.objectMapper = objectMapper;
    this.manifestEvaluator = manifestEvaluator;
  }

  public List<Map> getOperations(StageExecution stage) {
    Map<String, Object> operation = new HashMap<>();

    if (stage.getContext().containsKey("cluster")) {
      operation.putAll((Map) stage.getContext().get("cluster"));
    } else {
      operation.putAll(stage.getContext());
    }

    operation.putAll(getManifestFields(stage));

    KubernetesContainerFinder.populateFromStage(operation, stage, artifactUtils);

    Map<String, Object> task = new HashMap<>();
    task.put(OPERATION, operation);
    return Collections.singletonList(task);
  }

  // Gets the fields relevant to manifests that should be added to the operation
  private ImmutableMap<String, Object> getManifestFields(StageExecution stage) {
    RunJobManifestContext runJobManifestContext = stage.mapTo(RunJobManifestContext.class);

    // This short-circuit exists to handle jobs from the Kubernetes V1 provider; these have the
    // source set to Text (because it's the default and they don't set a source), but also have no
    // manifests. This will fail if we try to call manifestEvaluator.evaluate() so short-circuit
    // as the additional fields are not relevant. This workaround can be removed once the V1
    // provider is removed (currently scheduled for the 1.21 release).
    if (runJobManifestContext.getSource() == Source.Text
        && runJobManifestContext.getManifests() == null) {
      return ImmutableMap.of();
    }

    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, runJobManifestContext);

    List<Map<Object, Object>> manifests = result.getManifests();
    if (manifests.size() != 1) {
      throw new IllegalArgumentException("Run Job only supports manifests with a single Job.");
    }

    return ImmutableMap.of(
        "source", "text",
        "manifest", manifests.get(0),
        "requiredArtifacts", result.getRequiredArtifacts(),
        "optionalArtifacts", result.getOptionalArtifacts());
  }

  public Map<String, Object> getAdditionalOutputs(StageExecution stage, List<Map> operations) {
    Map<String, Object> outputs = new HashMap<>();
    Map<String, Object> execution = new HashMap<>();

    // if the manifest contains the template annotation put it into the context
    if (stage.getContext().containsKey("manifest")) {
      Manifest manifest =
          objectMapper.convertValue(stage.getContext().get("manifest"), Manifest.class);
      String logTemplate = ManifestAnnotationExtractor.logs(manifest);
      if (logTemplate != null) {
        execution.put("logs", logTemplate);
        outputs.put("execution", execution);
      }
    }

    return outputs;
  }
}
