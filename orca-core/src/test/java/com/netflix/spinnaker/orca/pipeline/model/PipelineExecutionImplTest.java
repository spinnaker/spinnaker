/*
 * Copyright 2024 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PipelineExecutionImplTest {

  private PipelineExecutionImpl pipelineExecution;

  private StageExecutionImpl stageExecution;

  @BeforeEach
  void setup() {
    pipelineExecution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test-application");
    stageExecution = new StageExecutionImpl();
    stageExecution.setExecution(pipelineExecution);
    pipelineExecution.getStages().add(stageExecution);
  }

  @Test
  void getTotalSizeMissingPipelineSize() {
    // given
    assertThat(pipelineExecution.getSize()).isEmpty();

    // then
    assertThat(pipelineExecution.getTotalSize()).isEmpty();
  }

  @Test
  void getTotalSizeMissingStageSize() {
    // given
    long pipelineSize = 14; // arbitrary

    pipelineExecution.setSize(pipelineSize);
    assertThat(pipelineExecution.getSize()).isPresent();

    assertThat(stageExecution.getSize()).isEmpty();

    // then
    assertThat(pipelineExecution.getTotalSize()).isEmpty();
  }

  @Test
  void getTotalSizeCompleteInfo() {
    // given
    long pipelineSize = 5; // arbitrary
    long stageSize = 7; // arbitrary

    pipelineExecution.setSize(pipelineSize);
    assertThat(pipelineExecution.getSize()).isPresent();

    stageExecution.setSize(stageSize);
    assertThat(stageExecution.getSize()).isPresent();

    // then
    assertThat(pipelineExecution.getTotalSize().get()).isEqualTo(pipelineSize + stageSize);
  }
}
