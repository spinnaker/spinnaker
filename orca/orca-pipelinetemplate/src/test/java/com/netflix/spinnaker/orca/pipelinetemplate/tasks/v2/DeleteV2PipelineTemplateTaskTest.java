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

package com.netflix.spinnaker.orca.pipelinetemplate.tasks.v2;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

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

public class DeleteV2PipelineTemplateTaskTest {

  @RegisterExtension
  static WireMockExtension front50Server =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static Front50Service front50Service;
  private DeleteV2PipelineTemplateTask task;
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

    task = new DeleteV2PipelineTemplateTask();

    // Use reflection to set the private front50Service field
    var field = DeleteV2PipelineTemplateTask.class.getDeclaredField("front50Service");
    field.setAccessible(true);
    field.set(task, front50Service);
  }

  @Test
  void shouldThrowExceptionWhenFront50ServiceIsNull() {
    DeleteV2PipelineTemplateTask localTask = new DeleteV2PipelineTemplateTask();
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplateId", "myTemplateId");

    assertThrows(
        UnsupportedOperationException.class,
        () -> localTask.execute(createStage(context)),
        "Front50 is not enabled, no way to delete pipeline. Fix this by setting front50.enabled: true");
  }

  @Test
  void shouldThrowExceptionWhenPipelineTemplateIdIsMissing() {
    Map<String, Object> context = new HashMap<>();

    assertThrows(
        IllegalArgumentException.class,
        () -> task.execute(createStage(context)),
        "Missing required task parameter (pipelineTemplateId)");
  }

  @Test
  void shouldDeletePipelineTemplateWithoutTagOrDigestSuccessfully() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplateId", "myTemplateId");

    front50Server.stubFor(
        delete(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

    var result = task.execute(createStage(context));

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("deletepipelinetemplate", result.getContext().get("notification.type"));
    assertEquals("myTemplateId", result.getContext().get("pipeline.id"));

    // Verify the request
    front50Server.verify(
        1,
        deleteRequestedFor(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", absent())
            .withQueryParam("digest", absent()));
  }

  @Test
  void shouldDeletePipelineTemplateWithTagSuccessfully() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplateId", "myTemplateId");
    context.put("tag", "prod");

    front50Server.stubFor(
        delete(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", equalTo("prod"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

    var result = task.execute(createStage(context));

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("deletepipelinetemplate", result.getContext().get("notification.type"));
    assertEquals("myTemplateId", result.getContext().get("pipeline.id"));

    // Verify the request
    front50Server.verify(
        1,
        deleteRequestedFor(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", equalTo("prod"))
            .withQueryParam("digest", absent()));
  }

  @Test
  void shouldDeletePipelineTemplateWithDigestSuccessfully() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplateId", "myTemplateId");
    context.put("digest", "sha256:abc123");

    front50Server.stubFor(
        delete(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("digest", equalTo("sha256:abc123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

    var result = task.execute(createStage(context));

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("deletepipelinetemplate", result.getContext().get("notification.type"));
    assertEquals("myTemplateId", result.getContext().get("pipeline.id"));

    // Verify the request
    front50Server.verify(
        1,
        deleteRequestedFor(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", absent())
            .withQueryParam("digest", equalTo("sha256:abc123")));
  }

  @Test
  void shouldDeletePipelineTemplateWithTagAndDigestSuccessfully() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplateId", "myTemplateId");
    context.put("tag", "prod");
    context.put("digest", "sha256:abc123");

    front50Server.stubFor(
        delete(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", equalTo("prod"))
            .withQueryParam("digest", equalTo("sha256:abc123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

    var result = task.execute(createStage(context));

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("deletepipelinetemplate", result.getContext().get("notification.type"));
    assertEquals("myTemplateId", result.getContext().get("pipeline.id"));

    // Verify the request
    front50Server.verify(
        1,
        deleteRequestedFor(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", equalTo("prod"))
            .withQueryParam("digest", equalTo("sha256:abc123")));
  }

  private StageExecution createStage(Map<String, Object> context) {
    return new StageExecutionImpl(
        PipelineExecutionImpl.newPipeline("test-application"), "deletePipelineTemplate", context);
  }
}
