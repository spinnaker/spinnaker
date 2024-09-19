/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.pipeline.tasks.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.config.TaskConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BindProducedArtifactsTaskTest {

  @Mock private ContextParameterProcessor contextParameterProcessor;
  @Mock private ExecutionRepository executionRepository;
  private TaskConfigurationProperties configService;
  private ObjectMapper objectMapper;
  private BindProducedArtifactsTask bindProducedArtifactsTask;
  private ArtifactUtils artifactUtils;

  @BeforeEach
  public void setup() {
    configService = new TaskConfigurationProperties();
    objectMapper = new ObjectMapper();
    artifactUtils = new ArtifactUtils(objectMapper, executionRepository, contextParameterProcessor);
  }

  @DisplayName(
      "parameterized test to see how keys in the outputs object are filtered for a bake manifest based on the inputs")
  @ParameterizedTest(name = "{index} => keys to be excluded from outputs = {0}")
  @ValueSource(strings = {"", "artifacts,resolvedExpectedArtifacts"})
  void testOutputFilterForBakeManifest(String keysToFilter) throws IOException {

    // setup
    Set<String> expectedKeysToBeExcludedFromOutput = new HashSet<>();
    if (!keysToFilter.equals("")) {
      expectedKeysToBeExcludedFromOutput = new HashSet<>(Arrays.asList(keysToFilter.split(",")));
    }

    configService
        .getBindProducedArtifactsTask()
        .setExcludeKeysFromOutputs(expectedKeysToBeExcludedFromOutput);

    bindProducedArtifactsTask =
        new BindProducedArtifactsTask(artifactUtils, objectMapper, configService);

    Map<String, Object> context =
        objectMapper.readValue(getResource("bake-manifest-context.json"), Map.class);
    StageExecution stageExecution =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"), "test", context);

    // when
    TaskResult result = bindProducedArtifactsTask.execute(stageExecution);

    // then

    // the 'outputs' key should not contain the values present in the input i.e. in `keysToFilter`
    if (expectedKeysToBeExcludedFromOutput.containsAll(
        List.of("artifacts", "resolvedExpectedArtifacts"))) {
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

  @DisplayName("test to see how what the task returns as output for a deploy manifest")
  @Test
  public void testOutputFilterForDeployManifest() throws IOException {
    // setup
    bindProducedArtifactsTask =
        new BindProducedArtifactsTask(artifactUtils, objectMapper, configService);

    Map<String, Object> context =
        objectMapper.readValue(getResource("deploy-manifest-context.json"), Map.class);
    StageExecution stageExecution =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"), "test", context);

    // when
    TaskResult result = bindProducedArtifactsTask.execute(stageExecution);

    // then

    // for deploy manifests, there is no expected Artifacts present in the stage context, so the
    // result won't contain either outputs or context properties
    assertThat(result.getOutputs().isEmpty());
    assertThat(result.getContext().isEmpty());
  }

  private InputStream getResource(String name) {
    return BindProducedArtifactsTaskTest.class.getResourceAsStream(name);
  }
}
