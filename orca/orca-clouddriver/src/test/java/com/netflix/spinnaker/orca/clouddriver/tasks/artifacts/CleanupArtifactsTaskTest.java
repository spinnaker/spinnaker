/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.artifacts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class CleanupArtifactsTaskTest {
  private KatoService mockKatoService;

  @BeforeEach
  public void setup() {
    mockKatoService = mock(KatoService.class);
  }

  @Test
  void testLabelSelectorsNotInContext() {
    // given:
    when(mockKatoService.requestOperations(anyString(), any()))
        .thenReturn(new TaskId("some-kato-task-id"));

    StageExecutionImpl myStage =
        createStageWithContext(
            ImmutableMap.of(
                "cloudProvider", "kubernetes",
                "account.name", "some-account"));

    // when:
    TaskResult _ignored = new CleanupArtifactsTask(mockKatoService).execute(myStage);

    // then:
    ArgumentCaptor<Collection<Map<String, Map>>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(mockKatoService).requestOperations(eq("kubernetes"), captor.capture());

    Map<String, Map> operationsArgument = captor.getValue().iterator().next();
    Map<String, List<Map<String, Object>>> operations =
        operationsArgument.get(CleanupArtifactsTask.TASK_NAME);
    assertFalse(operations.isEmpty());
    assertFalse(operations.containsKey("labelSelectors"));
  }

  @Test
  void testLabelSelectorsInContext() {
    // given:
    when(mockKatoService.requestOperations(anyString(), any()))
        .thenReturn(new TaskId("some-kato-task-id"));

    // labelSelectors": {
    //   "selectors": [
    //     {
    //       "key": "some-key",
    //       "kind": "EQUALS",
    //       "values": ["some-value"]
    //     }
    //   ]
    // }
    Map<String, List<Map<String, Object>>> labelSelectors = Map.of("selectors", new ArrayList<>());

    StageExecutionImpl myStage =
        createStageWithContext(
            ImmutableMap.of(
                "cloudProvider", "kubernetes",
                "account.name", "some-account",
                "labelSelectors", labelSelectors));

    // when:
    TaskResult _ignored = new CleanupArtifactsTask(mockKatoService).execute(myStage);

    // then:
    ArgumentCaptor<Collection<Map<String, Map>>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(mockKatoService).requestOperations(eq("kubernetes"), captor.capture());

    Map<String, Map> operationsArgument = captor.getValue().iterator().next();
    Map<String, List<Map<String, Object>>> operations =
        operationsArgument.get(CleanupArtifactsTask.TASK_NAME);
    assertFalse(operations.isEmpty());
    assertTrue(operations.containsKey("labelSelectors"));
  }

  private StageExecutionImpl createStageWithContext(Map<String, ?> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test-app"),
        "test",
        new HashMap<>(context));
  }
}
