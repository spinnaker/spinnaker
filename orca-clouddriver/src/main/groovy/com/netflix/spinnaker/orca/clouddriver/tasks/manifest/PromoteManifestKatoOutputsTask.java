/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PromoteManifestKatoOutputsTask implements Task {
  public static final String TASK_NAME = "promoteOutputs";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext();
    Map<String, Object> outputs = new HashMap<>();
    List<Map> tasks = (List<Map>) context.get("kato.tasks");
    tasks = tasks == null ? new ArrayList<>() : tasks;
    List<Map> allResults = tasks.stream()
        .map(t -> (List<Map>) t.getOrDefault("resultObjects", new ArrayList<>()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    addToOutputs(outputs, allResults, "manifests");
    addToOutputs(outputs, allResults, "manifestNamesByNamespace");
    addToOutputs(outputs, allResults, "boundArtifacts");
    addToOutputs(outputs, allResults, "createdArtifacts");
    addToOutputs(outputs, allResults, "createdArtifacts", "artifacts");

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs, outputs);
  }

  private void addToOutputs(Map<String, Object> outputs, List<Map> allResults, String key) {
    addToOutputs(outputs, allResults, key, "outputs." + key);
  }

  private void addToOutputs(Map<String, Object> outputs, List<Map> allResults, String key, String targetKey) {
    Optional value = allResults.stream()
        .map(m -> m.get(key))
        .filter(Objects::nonNull)
        .findFirst();

    value.ifPresent(m -> outputs.put(targetKey, m));
  }
}
