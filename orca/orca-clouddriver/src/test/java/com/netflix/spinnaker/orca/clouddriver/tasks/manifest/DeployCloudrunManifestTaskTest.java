/*
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
package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeployCloudrunManifestTaskTest {

  private KatoService kato;
  private DeployCloudrunManifestTask task;

  @BeforeEach
  void setUp() {
    kato = mock(KatoService.class);
    task = new DeployCloudrunManifestTask(kato);
    when(kato.requestOperations(any(String.class), any()))
        .thenReturn(new TaskId(UUID.randomUUID().toString()));
  }

  @Test
  void usesCloudrunWhenTheStageHasNoCloudProvider() {
    TaskResult result = task.execute(stageWith(new HashMap<>()));

    verify(kato).requestOperations(eq("cloudrun"), any());
    assertThat(result.getContext().get("cloudProvider")).isEqualTo("cloudrun");
  }

  @Test
  void usesTheCloudProviderFromTheStageWhenPresent() {
    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", "custom");

    task.execute(stageWith(context));

    verify(kato).requestOperations(eq("custom"), any());
  }

  private StageExecution stageWith(Map<String, Object> context) {
    context.put("account", "my-account");
    StageExecution stage = new StageExecutionImpl();
    stage.setType(DeployCloudrunManifestTask.TASK_NAME);
    stage.setContext(context);
    return stage;
  }
}
