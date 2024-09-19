/*
 * Copyright 2021 Armory, Inc.
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

import static com.netflix.spinnaker.orca.TestUtils.getResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.config.TaskConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ResolveDeploySourceManifestTaskTest {

  private static final Map<Object, Object> MANIFEST_1 =
      ImmutableMap.of("test-key-1", "test-value-1");
  private static final Map<Object, Object> MANIFEST_2 =
      ImmutableMap.of("test-key-2", "test-value-2");
  private final TaskConfigurationProperties configProperties = new TaskConfigurationProperties();

  private ObjectMapper objectMapper;
  private ArtifactUtils artifactUtils;
  private ManifestEvaluator manifestEvaluator;
  private OortService oortService = mock(OortService.class);
  private ExecutionRepository executionRepository = mock(ExecutionRepository.class);
  private ContextParameterProcessor contextParameterProcessor =
      mock(ContextParameterProcessor.class);

  @BeforeEach
  public void setup() {
    objectMapper = new ObjectMapper();
    artifactUtils = new ArtifactUtils(objectMapper, executionRepository, contextParameterProcessor);
    manifestEvaluator =
        new ManifestEvaluator(
            artifactUtils, contextParameterProcessor, oortService, new RetrySupport());
  }

  @Test
  void manifestsWithObjectsIsNotFlattened() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task =
        new ResolveDeploySourceManifestTask(manifestEvaluator, configProperties);

    StageExecutionImpl myStage = createStageWithManifests(ImmutableList.of(MANIFEST_1, MANIFEST_2));

    DeployManifestContext deployManifestContext =
        DeployManifestContext.builder().manifests(ImmutableList.of(MANIFEST_1, MANIFEST_2)).build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1, MANIFEST_2), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
    assertThat(getManifests(result)).containsExactly(MANIFEST_1, MANIFEST_2);
  }

  @Test
  void manifestsWithListIsFlattened() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task =
        new ResolveDeploySourceManifestTask(manifestEvaluator, configProperties);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableList.of(ImmutableList.of(MANIFEST_1, MANIFEST_2)));

    DeployManifestContext deployManifestContext =
        DeployManifestContext.builder().manifests(ImmutableList.of(MANIFEST_1, MANIFEST_2)).build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1, MANIFEST_2), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
    assertThat(getManifests(result)).containsExactly(MANIFEST_1, MANIFEST_2);
  }

  @Test
  void manifestsWithListsAndObjectsIsFlattened() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task =
        new ResolveDeploySourceManifestTask(manifestEvaluator, configProperties);

    StageExecutionImpl myStage =
        createStageWithManifests(ImmutableList.of(ImmutableList.of(MANIFEST_1), MANIFEST_2));

    DeployManifestContext deployManifestContext =
        DeployManifestContext.builder().manifests(ImmutableList.of(MANIFEST_1, MANIFEST_2)).build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1, MANIFEST_2), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
    assertThat(getManifests(result)).containsExactly(MANIFEST_1, MANIFEST_2);
  }

  @Test
  void manifestsWithListsIsFlattened() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task =
        new ResolveDeploySourceManifestTask(manifestEvaluator, configProperties);

    StageExecutionImpl myStage =
        createStageWithManifests(
            ImmutableList.of(ImmutableList.of(MANIFEST_1), ImmutableList.of(MANIFEST_2)));

    DeployManifestContext deployManifestContext =
        DeployManifestContext.builder().manifests(ImmutableList.of(MANIFEST_1, MANIFEST_2)).build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1, MANIFEST_2), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
    assertThat(getManifests(result)).containsExactly(MANIFEST_1, MANIFEST_2);
  }

  @Test
  void stageWithoutManifestsHandled() {
    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ResolveDeploySourceManifestTask task =
        new ResolveDeploySourceManifestTask(manifestEvaluator, configProperties);

    StageExecutionImpl myStage = createStageWithManifests(null);

    DeployManifestContext deployManifestContext = DeployManifestContext.builder().build();

    when(manifestEvaluator.evaluate(any(), eq(deployManifestContext)))
        .thenReturn(
            new ManifestEvaluator.Result(
                ImmutableList.of(MANIFEST_1), ImmutableList.of(), ImmutableList.of()));

    TaskResult result = task.execute(myStage);
    verify(manifestEvaluator, times(1)).evaluate(any(), eq(deployManifestContext));
  }

  private StageExecutionImpl createStageWithManifests(ImmutableList<Object> manifestsByNamespace) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"),
        "test",
        manifestsByNamespace != null
            ? new HashMap<>(ImmutableMap.of("manifests", manifestsByNamespace))
            : new HashMap<>());
  }

  private static List<Map<Object, Object>> getManifests(TaskResult result) {
    Map<String, ?> context = result.getContext();
    return Optional.ofNullable(context)
        .map(c -> (List<Map<Object, Object>>) c.get("manifests"))
        .orElse(ImmutableList.of());
  }

  @DisplayName(
      "parameterized test to see how keys in the outputs object are filtered based on the inputs")
  @ParameterizedTest(name = "{index} ==> keys to be excluded from outputs = {0}")
  @ValueSource(strings = {"", "manifests,requiredArtifacts,optionalArtifacts"})
  void testOutputFilter(String keysToFilter) {

    // setup
    Set<String> expectedKeysToBeExcludedFromOutput = new HashSet<>();
    if (!keysToFilter.equals("")) {
      expectedKeysToBeExcludedFromOutput = new HashSet<>(Arrays.asList(keysToFilter.split(",")));
    }

    configProperties
        .getResolveDeploySourceManifestTask()
        .setExcludeKeysFromOutputs(expectedKeysToBeExcludedFromOutput);

    ResolveDeploySourceManifestTask resolveDeploySourceManifestTask =
        new ResolveDeploySourceManifestTask(manifestEvaluator, configProperties);

    Map<String, Object> context =
        getResource(
            objectMapper, "clouddriver/tasks/manifest/deploy-manifest-context.json", Map.class);
    StageExecution stageExecution =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"), "test", context);

    // when
    TaskResult result = resolveDeploySourceManifestTask.execute(stageExecution);

    // then

    // the 'outputs' key should not contain the values present in the input i.e. in `keysToFilter`
    if (expectedKeysToBeExcludedFromOutput.containsAll(
        List.of("manifests", "requiredArtifacts", "optionalArtifacts"))) {
      assertThat(result.getOutputs().isEmpty());
    } else {
      assertThat(result.getOutputs()).isNotEmpty();
    }
    assertThat(result.getContext()).isNotEmpty();

    Set<String> receivedOutputsKeySet = result.getOutputs().keySet();
    for (String excludedKey : expectedKeysToBeExcludedFromOutput) {
      assertThat(receivedOutputsKeySet.contains(excludedKey)).isFalse();
    }

    // ensuring that the 'context' key still has the values present in the input i.e. in
    // `keysToFilter`
    Set<String> receivedContextKeySet = result.getContext().keySet();
    for (String excludedKey : expectedKeysToBeExcludedFromOutput) {
      assertThat(receivedContextKeySet.contains(excludedKey)).isTrue();
    }
  }
}
