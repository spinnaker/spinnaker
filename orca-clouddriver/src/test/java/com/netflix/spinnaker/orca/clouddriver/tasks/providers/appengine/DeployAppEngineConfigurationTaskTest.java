/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.Map;
import org.junit.Test;

public class DeployAppEngineConfigurationTaskTest {
  private final String CLOUD_OPERATION_TYPE = "deployAppengineConfiguration";

  ObjectMapper mapper = new ObjectMapper();
  KatoService katoService = mock(KatoService.class);
  ArtifactUtils artifactUtils = mock(ArtifactUtils.class);
  DeployAppEngineConfigurationTask deployAppEngineConfigurationTask =
      new DeployAppEngineConfigurationTask(mapper, katoService, artifactUtils);

  @Test
  public void shouldMapAndDeploy() throws JsonProcessingException {
    String json =
        "{\n"
            + "  \"account\": \"my-appengine-account\",\n"
            + "  \"cronArtifact\": {\n"
            + "    \"account\": \"embedded-artifact\",\n"
            + "    \"artifact\": {\n"
            + "      \"artifactAccount\": \"embedded-artifact\",\n"
            + "      \"id\": \"04fa7b32-fd17-4536-87b5-2a0d7b58c87c\",\n"
            + "      \"name\": \"blah\",\n"
            + "      \"reference\": \"ZG9zb21ldGhpbmc=\",\n"
            + "      \"type\": \"embedded/base64\"\n"
            + "    },\n"
            + "    \"id\": null\n"
            + "  },\n"
            + "  \"name\": \"Deploy Global AppEngine Configuration\",\n"
            + "  \"region\": \"us-east1\",\n"
            + "  \"type\": \"deployAppengineConfig\"\n"
            + "}";

    Map<String, Object> input = mapper.readValue(json, Map.class);
    StageExecution stageExecution = new StageExecutionImpl();
    stageExecution.setContext(input);

    when(katoService.requestOperations(any(), any())).thenReturn(new TaskId("taskid"));

    Map<String, Object> expectedContext =
        new ImmutableMap.Builder<String, Object>()
            .put("notification.type", CLOUD_OPERATION_TYPE)
            .put("kato.last.task.id", new TaskId("taskid"))
            .put("service.region", "us-east1")
            .put("service.account", deployAppEngineConfigurationTask.getCredentials(stageExecution))
            .build();

    TaskResult expected =
        TaskResult.builder(ExecutionStatus.SUCCEEDED).context(expectedContext).build();

    TaskResult result =
        deployAppEngineConfigurationTask.execute(
            new StageExecutionImpl(
                new PipelineExecutionImpl(PIPELINE, "orca"),
                CLOUD_OPERATION_TYPE,
                stageExecution.getContext()));

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldThrowWhenNoArtifactsSelected() throws JsonProcessingException {
    String json =
        "{\n"
            + "  \"account\": \"my-appengine-account\",\n"
            + "  \"name\": \"Deploy Global AppEngine Configuration\",\n"
            + "  \"region\": \"us-east1\",\n"
            + "  \"type\": \"deployAppengineConfig\"\n"
            + "}";

    Map<String, Object> input = mapper.readValue(json, Map.class);
    StageExecution stageExecution = new StageExecutionImpl();
    stageExecution.setContext(input);

    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              deployAppEngineConfigurationTask.execute(stageExecution);
            });

    String message = exception.getMessage();
    assertTrue(message.equals("At least one configuration artifact must be supplied."));
  }
}
