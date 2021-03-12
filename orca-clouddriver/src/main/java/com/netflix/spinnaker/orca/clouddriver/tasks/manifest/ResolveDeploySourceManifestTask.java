/*
 * Copyright 2020 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public final class ResolveDeploySourceManifestTask implements Task {
  public static final String TASK_NAME = "resolveDeploySourceManifest";

  private final ManifestEvaluator manifestEvaluator;

  @Autowired
  public ResolveDeploySourceManifestTask(ManifestEvaluator manifestEvaluator) {
    this.manifestEvaluator = manifestEvaluator;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    List<Object> manifests = flattenNestedLists(stage.getContext().get("manifests"));
    stage.getContext().put("manifests", manifests);
    DeployManifestContext context = stage.mapTo(DeployManifestContext.class);
    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, context);
    ImmutableMap<String, Object> outputs = getOutputs(result);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }

  private ImmutableMap<String, Object> getOutputs(ManifestEvaluator.Result result) {
    return new ImmutableMap.Builder<String, Object>()
        .put("manifests", result.getManifests())
        .put("requiredArtifacts", result.getRequiredArtifacts())
        .put("optionalArtifacts", result.getOptionalArtifacts())
        .build();
  }

  // DeployManifestContext only accepts List<Map<Object, Object>>,
  // Spel is not flattening nested List anymore
  // We flatten them because its possible have a List of manifests resolved by spel
  private List<Object> flattenNestedLists(Object manifests) {
    if (manifests == null) {
      return null;
    }

    List<Object> result = new ArrayList<>();
    List<Object> tmp = (List<Object>) manifests;

    for (Object obj : tmp) {
      if (obj instanceof List) {
        result.addAll(flattenNestedLists(obj));
      } else {
        result.add(obj);
      }
    }
    return result;
  }
}
