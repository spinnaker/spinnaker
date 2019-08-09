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
import com.netflix.spinnaker.orca.clouddriver.tasks.job.JobRunner;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.RunJobManifestContext;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import java.util.*;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class KubernetesJobRunner implements JobRunner {

  private boolean katoResultExpected = false;
  private String cloudProvider = "kubernetes";

  private ArtifactResolver artifactResolver;
  private ObjectMapper objectMapper;
  private ManifestEvaluator manifestEvaluator;

  public KubernetesJobRunner(
      ArtifactResolver artifactResolver,
      ObjectMapper objectMapper,
      ManifestEvaluator manifestEvaluator) {
    this.artifactResolver = artifactResolver;
    this.objectMapper = objectMapper;
    this.manifestEvaluator = manifestEvaluator;
  }

  public List<Map> getOperations(Stage stage) {
    Map<String, Object> operation = new HashMap<>();

    if (stage.getContext().containsKey("cluster")) {
      operation.putAll((Map) stage.getContext().get("cluster"));
    } else {
      operation.putAll(stage.getContext());
    }

    RunJobManifestContext runJobManifestContext = stage.mapTo(RunJobManifestContext.class);
    if (runJobManifestContext.getSource().equals(ManifestContext.Source.Artifact)) {
      ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, runJobManifestContext);

      List<Map<Object, Object>> manifests = result.getManifests();
      if (manifests.size() != 1) {
        throw new IllegalArgumentException("Run Job only supports manifests with a single Job.");
      }

      operation.put("source", "text");
      operation.put("manifest", manifests.get(0));
      operation.put("requiredArtifacts", result.getRequiredArtifacts());
      operation.put("optionalArtifacts", result.getOptionalArtifacts());
    }

    KubernetesContainerFinder.populateFromStage(operation, stage, artifactResolver);

    Map<String, Object> task = new HashMap<>();
    task.put(OPERATION, operation);
    return Collections.singletonList(task);
  }

  public Map<String, Object> getAdditionalOutputs(Stage stage, List<Map> operations) {
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
