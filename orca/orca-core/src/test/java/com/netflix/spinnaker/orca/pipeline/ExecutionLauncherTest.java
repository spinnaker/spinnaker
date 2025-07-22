/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.config.ExecutionConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

public class ExecutionLauncherTest {

  private static final String TEST_APPLICATION = "test-application";
  private static final String TEST_PIPELINE_NAME = "test-pipeline-name";
  private static final String TEST_EXECUTION_ID = "test-execution-id";

  private ObjectMapper objectMapper = mock(ObjectMapper.class);
  private ExecutionRepository executionRepository = mock(ExecutionRepository.class);
  private ExecutionRunner executionRunner = mock(ExecutionRunner.class);
  private ApplicationEventPublisher applicationEventPublisher =
      mock(ApplicationEventPublisher.class);
  private ExecutionConfigurationProperties executionConfigurationProperties =
      new ExecutionConfigurationProperties();

  ExecutionLauncher executionLauncher =
      new ExecutionLauncher(
          objectMapper,
          executionRepository,
          executionRunner,
          Clock.systemUTC(),
          applicationEventPublisher,
          Optional.empty() /* pipelineValidator */,
          Optional.empty() /* registry */,
          executionConfigurationProperties);

  @Test
  public void parsePipelinePopulatesRootId() {
    Map<String, Object> pipelineConfig =
        Map.of(
            "application", TEST_APPLICATION,
            "name", TEST_PIPELINE_NAME,
            "executionId", TEST_EXECUTION_ID,
            "stages", List.of());

    // When passed a non-null / non-empty rootId, expect it in the resulting
    // PipelineExecution.  This is the case for child pipelines.
    String rootId = "parent-pipeline-root-id";
    PipelineExecution childPipelineExecution =
        executionLauncher.parsePipeline(pipelineConfig, rootId);
    assertThat(childPipelineExecution.getRootId()).isEqualTo(rootId);

    // When passed a null or empty root Id, expect the root id to be the
    // execution id of the pipeline.  This is the case for a top-level pipeline.
    PipelineExecution topLevelPipelineExecution =
        executionLauncher.parsePipeline(pipelineConfig, null);
    assertThat(topLevelPipelineExecution.getRootId()).isEqualTo(TEST_EXECUTION_ID);

    PipelineExecution topLevelPipelineExecution2 =
        executionLauncher.parsePipeline(pipelineConfig, "");
    assertThat(topLevelPipelineExecution2.getRootId()).isEqualTo(TEST_EXECUTION_ID);
  }
}
