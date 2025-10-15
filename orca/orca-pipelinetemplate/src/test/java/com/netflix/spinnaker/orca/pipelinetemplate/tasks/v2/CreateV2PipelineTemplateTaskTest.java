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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class CreateV2PipelineTemplateTaskTest {

  @RegisterExtension
  static WireMockExtension front50Server =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static Front50Service front50Service;
  private CreateV2PipelineTemplateTask task;
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

    task = new CreateV2PipelineTemplateTask();

    // Use reflection to set the private fields
    var front50ServiceField = CreateV2PipelineTemplateTask.class.getDeclaredField("front50Service");
    front50ServiceField.setAccessible(true);
    front50ServiceField.set(task, front50Service);

    var objectMapperField =
        CreateV2PipelineTemplateTask.class.getDeclaredField("pipelineTemplateObjectMapper");
    objectMapperField.setAccessible(true);
    objectMapperField.set(task, objectMapper);
  }

  @Test
  void shouldThrowExceptionWhenFront50ServiceIsNull() {
    CreateV2PipelineTemplateTask localTask = new CreateV2PipelineTemplateTask();
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", createValidBase64EncodedTemplate());

    assertThrows(
        UnsupportedOperationException.class,
        () -> localTask.execute(createStage(context)),
        "Front50 is not enabled, no way to save pipeline templates. Fix this by setting front50.enabled: true");
  }

  @Test
  void shouldThrowExceptionWhenPipelineTemplateIsMissing() {
    Map<String, Object> context = new HashMap<>();

    assertThrows(
        IllegalArgumentException.class,
        () -> task.execute(createStage(context)),
        "Missing required task parameter (pipelineTemplate)");
  }

  @Test
  void shouldThrowExceptionWhenPipelineTemplateIsNotBase64Encoded() {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", "not-base64-encoded");

    assertThrows(
        IllegalArgumentException.class,
        () -> task.execute(createStage(context)),
        "'pipelineTemplate' context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
  }

  @Test
  void shouldCreatePipelineTemplateSuccessfully() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", createValidBase64EncodedTemplate());

    front50Server.stubFor(
        post(urlPathEqualTo("/v2/pipelineTemplates"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"id\": \"myTemplateId\" }")));

    var result = task.execute(createStage(context));

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("createpipelinetemplate", result.getContext().get("notification.type"));
    assertEquals("myTemplateId", result.getContext().get("pipelineTemplate.id"));

    // Verify the request was made
    front50Server.verify(
        1,
        postRequestedFor(urlPathEqualTo("/v2/pipelineTemplates")).withQueryParam("tag", absent()));
  }

  @Test
  void shouldCreatePipelineTemplateWithTagSuccessfully() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", createValidBase64EncodedTemplate());
    context.put("tag", "prod");

    front50Server.stubFor(
        post(urlPathEqualTo("/v2/pipelineTemplates"))
            .withQueryParam("tag", equalTo("prod"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"id\": \"myTemplateId\" }")));

    var result = task.execute(createStage(context));

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("createpipelinetemplate", result.getContext().get("notification.type"));
    assertEquals("myTemplateId:prod", result.getContext().get("pipelineTemplate.id"));

    // Verify the request was made with the tag parameter
    front50Server.verify(
        1,
        postRequestedFor(urlPathEqualTo("/v2/pipelineTemplates"))
            .withQueryParam("tag", equalTo("prod")));
  }

  private String createValidBase64EncodedTemplate() {
    try {
      V2PipelineTemplate template = new V2PipelineTemplate();
      template.setId("myTemplateId");
      template.setSchema(V2PipelineTemplate.V2_SCHEMA_VERSION);

      V2PipelineTemplate.Metadata metadata = new V2PipelineTemplate.Metadata();
      metadata.setName("My Template");
      metadata.setDescription("A test template");
      metadata.setScopes(Collections.singletonList("global"));
      template.setMetadata(metadata);

      Map<String, Object> pipeline = new HashMap<>();
      pipeline.put("application", "myapp");
      pipeline.put("name", "My Pipeline");
      template.setPipeline(pipeline);

      String json = objectMapper.writeValueAsString(template);
      return Base64.encodeBase64String(json.getBytes());
    } catch (Exception e) {
      throw new RuntimeException("Failed to create template", e);
    }
  }

  private StageExecution createStage(Map<String, Object> context) {
    return new StageExecutionImpl(
        PipelineExecutionImpl.newPipeline("test-application"), "createPipelineTemplate", context);
  }
}
