/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.objectstorage.UploadArtifactToObjectStorageTask;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UploadArtifactToObjectStorageStageTest {

  private UploadArtifactToObjectStorageStage stage;

  @BeforeEach
  void setUp() {
    stage = new UploadArtifactToObjectStorageStage();
  }

  @Test
  void shouldBuildCorrectTaskGraph() {
    // Given
    StageExecutionImpl stageExecution = new StageExecutionImpl();
    stageExecution.setContext(new HashMap<>());

    // When
    TaskNode.TaskGraph taskGraph = stage.buildTaskGraph(stageExecution);

    // Then
    List<String> taskNames = new ArrayList<>();
    taskGraph
        .iterator()
        .forEachRemaining(
            node -> {
              if (node instanceof TaskNode.TaskDefinition) {
                taskNames.add(((TaskNode.TaskDefinition) node).getName());
              }
            });

    assertThat(taskNames).containsExactly("uploadArtifactToObjectStorage", "monitorUpload");
  }

  @Test
  void shouldUseCorrectTaskImplementations() {
    // Given
    StageExecutionImpl stageExecution = new StageExecutionImpl();
    stageExecution.setContext(new HashMap<>());

    // When
    TaskNode.TaskGraph taskGraph = stage.buildTaskGraph(stageExecution);

    // Then
    List<Class<?>> taskClasses = new ArrayList<>();
    taskGraph
        .iterator()
        .forEachRemaining(
            node -> {
              if (node instanceof TaskNode.TaskDefinition) {
                taskClasses.add(((TaskNode.TaskDefinition) node).getImplementingClass());
              }
            });

    assertThat(taskClasses)
        .containsExactly(UploadArtifactToObjectStorageTask.class, MonitorKatoTask.class);
  }

  @Test
  void shouldHaveCorrectStageType() {
    // When
    String type = stage.getType();

    // Then
    assertThat(type).isEqualTo("uploadArtifactToObjectStorage");
  }
}
