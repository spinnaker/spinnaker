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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import static com.netflix.spinnaker.orca.TestUtils.getResource;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.TaskConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PromoteManifestKatoOutputsTaskTest {

  private PromoteManifestKatoOutputsTask promoteManifestKatoOutputsTask;
  private TaskConfigurationProperties configService;
  private ObjectMapper objectMapper;

  @BeforeEach
  public void setup() {
    configService = new TaskConfigurationProperties();
    objectMapper = new ObjectMapper();
  }

  @DisplayName("test to see how keys in the outputs object are filtered based on the inputs")
  @ParameterizedTest(name = "{index} ==> keys to be excluded from outputs = {0}")
  @ValueSource(
      strings = {"", "outputs.createdArtifacts,outputs.manifests,outputs.boundArtifacts,artifacts"})
  void testOutputFilter(String keysToFilter) {

    // setup
    Set<String> expectedKeysToBeExcludedFromOutput = new HashSet<>();
    if (!keysToFilter.equals("")) {
      expectedKeysToBeExcludedFromOutput = new HashSet<>(Arrays.asList(keysToFilter.split(",")));
    }

    configService
        .getPromoteManifestKatoOutputsTask()
        .setExcludeKeysFromOutputs(expectedKeysToBeExcludedFromOutput);
    promoteManifestKatoOutputsTask =
        new PromoteManifestKatoOutputsTask(objectMapper, configService);

    Map<String, Object> context =
        getResource(
            objectMapper, "clouddriver/tasks/manifest/deploy-manifest-context.json", Map.class);
    StageExecution stageExecution =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"), "test", context);

    // when
    TaskResult result = promoteManifestKatoOutputsTask.execute(stageExecution);

    // then
    assertThat(result.getOutputs()).isNotEmpty();
    assertThat(result.getContext()).isNotEmpty();

    // the 'outputs' key should not contain the values present in the input i.e. in `keysToFilter`
    Set<String> receivedOutputsKeySet = result.getOutputs().keySet();
    for (String excludedKey : expectedKeysToBeExcludedFromOutput) {
      if (!excludedKey.isEmpty()) {
        assertThat(receivedOutputsKeySet.contains(excludedKey)).isFalse();
      }
    }

    // ensuring that the 'context' key still has the values present in the input i.e. in
    // `keysToFilter`
    Set<String> receivedContextKeySet = result.getContext().keySet();
    for (String excludedKey : expectedKeysToBeExcludedFromOutput) {
      if (!excludedKey.isEmpty()) {
        assertThat(receivedContextKeySet.contains(excludedKey)).isTrue();
      }
    }
  }
}
