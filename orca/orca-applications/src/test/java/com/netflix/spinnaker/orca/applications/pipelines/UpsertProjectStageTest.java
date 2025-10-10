/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.orca.applications.pipelines;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class UpsertProjectStageTest {

  @RegisterExtension
  static WireMockExtension front50Server =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static Front50Service front50Service;
  private UpsertProjectStage.UpsertProjectTask task;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setup() throws Exception {
    if (front50Service == null) {
      front50Service =
          new Retrofit.Builder()
              .baseUrl(front50Server.baseUrl())
              .client(new OkHttpClient())
              .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
              .addConverterFactory(JacksonConverterFactory.create(objectMapper))
              .build()
              .create(Front50Service.class);
    }

    task = new UpsertProjectStage.UpsertProjectTask();

    // Use reflection to set the private front50Service field
    var field = UpsertProjectStage.UpsertProjectTask.class.getDeclaredField("front50Service");
    field.setAccessible(true);
    field.set(task, front50Service);
  }

  @Test
  void shouldCreateNewProject() throws Exception {
    // Given
    Map<String, Object> context = new HashMap<>();
    Map<String, Object> projectData = new HashMap<>();
    projectData.put("name", "test-project");
    projectData.put("email", "test@example.com");
    context.put("project", projectData);

    Front50Service.Project expectedProject = new Front50Service.Project();
    expectedProject.setId("new-id");
    expectedProject.setName("test-project");

    front50Server.stubFor(
        post(urlEqualTo("/v2/projects"))
            .willReturn(okJson(objectMapper.writeValueAsString(expectedProject))));

    // When
    var result = task.execute(createStage(context));

    // Then
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("upsertproject", result.getContext().get("notification.type"));

    // Verify the request
    front50Server.verify(postRequestedFor(urlEqualTo("/v2/projects")));
  }

  @Test
  void shouldUpdateExistingProject() throws Exception {
    // Given
    Map<String, Object> context = new HashMap<>();
    Map<String, Object> projectData = new HashMap<>();
    projectData.put("id", "existing-id");
    projectData.put("name", "updated-project");
    projectData.put("email", "updated@example.com");
    context.put("project", projectData);

    Front50Service.Project expectedProject = new Front50Service.Project();
    expectedProject.setId("existing-id");
    expectedProject.setName("updated-project");

    front50Server.stubFor(
        put(urlEqualTo("/v2/projects/existing-id"))
            .willReturn(okJson(objectMapper.writeValueAsString(expectedProject))));

    // When
    var result = task.execute(createStage(context));

    // Then
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("upsertproject", result.getContext().get("notification.type"));

    // Verify the request
    front50Server.verify(putRequestedFor(urlEqualTo("/v2/projects/existing-id")));
  }

  private StageExecution createStage(Map<String, Object> context) {
    return new StageExecutionImpl(
        PipelineExecutionImpl.newPipeline("test-application"), "upsertProject", context);
  }
}
