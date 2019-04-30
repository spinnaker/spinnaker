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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@Slf4j
public class PromoteManifestKatoOutputsTask implements Task {
  public static final String TASK_NAME = "promoteOutputs";

  private static final TypeReference<List<Artifact>> artifactListType = new TypeReference<List<Artifact>>() { };
  private static final String MANIFESTS_KEY = "manifests";
  private static final String MANIFESTS_BY_NAMESPACE_KEY = "manifestNamesByNamespace";
  private static final String BOUND_ARTIFACTS_KEY = "boundArtifacts";
  private static final String CREATED_ARTIFACTS_KEY = "createdArtifacts";
  private static final String ARTIFACTS_KEY = "artifacts";

  @Autowired
  ObjectMapper objectMapper;

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

    addToOutputs(outputs, allResults, MANIFESTS_KEY);
    addToOutputs(outputs, allResults, MANIFESTS_BY_NAMESPACE_KEY);

    addToOutputs(outputs, allResults, BOUND_ARTIFACTS_KEY);
    convertKey(outputs, outputKey(BOUND_ARTIFACTS_KEY), artifactListType);

    addToOutputs(outputs, allResults, CREATED_ARTIFACTS_KEY);
    convertKey(outputs, outputKey(CREATED_ARTIFACTS_KEY), artifactListType);

    addToOutputs(outputs, allResults, CREATED_ARTIFACTS_KEY, ARTIFACTS_KEY);
    convertKey(outputs, ARTIFACTS_KEY, artifactListType);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }

  private void convertKey(Map<String, Object> outputs, String key, TypeReference tr) {
    outputs.computeIfPresent(key, (k, v) -> objectMapper.convertValue(v, tr));
  }

  private String outputKey(String input) {
    return "outputs." + input;
  }

  private void addToOutputs(Map<String, Object> outputs, List<Map> allResults, String key) {
    addToOutputs(outputs, allResults, key, outputKey(key));
  }

  private void addToOutputs(Map<String, Object> outputs, List<Map> allResults, String key, String targetKey) {
    Optional value = allResults.stream()
        .map(m -> m.get(key))
        .filter(Objects::nonNull)
        .findFirst();

    value.ifPresent(m -> outputs.put(targetKey, m));
  }
}
