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
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import java.util.*;
import okhttp3.OkHttpClient;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class UpdateV2PipelineTemplateTaskTest {

  @RegisterExtension
  static WireMockExtension front50Server =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static Front50Service front50Service;
  private UpdateV2PipelineTemplateTask task;
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

    task = new UpdateV2PipelineTemplateTask();

    // Use reflection to set the private fields
    var front50ServiceField = UpdateV2PipelineTemplateTask.class.getDeclaredField("front50Service");
    front50ServiceField.setAccessible(true);
    front50ServiceField.set(task, front50Service);

    var objectMapperField =
        UpdateV2PipelineTemplateTask.class.getDeclaredField("pipelineTemplateObjectMapper");
    objectMapperField.setAccessible(true);
    objectMapperField.set(task, objectMapper);
  }

  @Test
  void shouldThrowExceptionWhenFront50ServiceIsNull() {
    UpdateV2PipelineTemplateTask localTask = new UpdateV2PipelineTemplateTask();
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", createValidBase64EncodedTemplate());
    context.put("id", "myTemplateId");

    assertThrows(
        UnsupportedOperationException.class,
        () -> localTask.execute(createStage(context)),
        "Front50 is not enabled, no way to save pipeline templates. Fix this by setting front50.enabled: true");
  }

  @Test
  void shouldThrowExceptionWhenPipelineTemplateIsMissing() {
    Map<String, Object> context = new HashMap<>();
    context.put("id", "myTemplateId");

    assertThrows(
        IllegalArgumentException.class,
        () -> task.execute(createStage(context)),
        "Missing required task parameter (pipelineTemplate)");
  }

  @Test
  void shouldThrowExceptionWhenIdIsMissing() {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", createValidBase64EncodedTemplate());

    assertThrows(
        IllegalArgumentException.class,
        () -> task.execute(createStage(context)),
        "Missing required task parameter (id)");
  }

  @Test
  void shouldThrowExceptionWhenPipelineTemplateIsNotBase64Encoded() {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", "not-base64-encoded");
    context.put("id", "myTemplateId");

    assertThrows(
        IllegalArgumentException.class,
        () -> task.execute(createStage(context)),
        "'pipelineTemplate' context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
  }

  @Test
  void shouldUpdatePipelineTemplateSuccessfully() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", createValidBase64EncodedTemplate());
    context.put("id", "myTemplateId");

    front50Server.stubFor(
        put(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"id\": \"myTemplateId\" }")));

    var result = task.execute(createStage(context));

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("updatepipelinetemplate", result.getContext().get("notification.type"));
    assertEquals("myTemplateId", result.getContext().get("pipelineTemplate.id"));

    // Verify the request was made
    front50Server.verify(
        1,
        putRequestedFor(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", absent()));
  }

  @Test
  void shouldUpdatePipelineTemplateWithTagSuccessfully() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", createValidBase64EncodedTemplate());
    context.put("id", "myTemplateId");
    context.put("tag", "prod");

    front50Server.stubFor(
        put(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", equalTo("prod"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"id\": \"myTemplateId\" }")));

    var result = task.execute(createStage(context));

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("updatepipelinetemplate", result.getContext().get("notification.type"));
    assertEquals("myTemplateId:prod", result.getContext().get("pipelineTemplate.id"));

    // Verify the request was made with the tag parameter
    front50Server.verify(
        1,
        putRequestedFor(urlPathEqualTo("/v2/pipelineTemplates/myTemplateId"))
            .withQueryParam("tag", equalTo("prod")));
  }

  @Test
  void shouldHandleIdMismatch() throws Exception {
    // Given - create a template with ID that differs from the context ID
    Map<String, Object> context = new HashMap<>();
    context.put(
        "pipelineTemplate",
        createValidBase64EncodedTemplate()); // Creates template with ID "myTemplateId"
    context.put("id", "differentTemplateId"); // API call will use this ID

    front50Server.stubFor(
        put(urlPathEqualTo(
                "/v2/pipelineTemplates/differentTemplateId")) // The URL uses the context ID
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"id\": \"differentTemplateId\" }")));

    // When
    var result = task.execute(createStage(context));

    // Then - the output uses the template ID, not the context ID
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals("myTemplateId", result.getContext().get("pipelineTemplate.id"));

    // Verify the request was made with the correct ID from context
    front50Server.verify(
        1, putRequestedFor(urlPathEqualTo("/v2/pipelineTemplates/differentTemplateId")));
  }

  @Test
  void shouldValidateTemplateThroughSaveV2PipelineTemplateTask() throws Exception {
    // Given - create a template with invalid properties
    V2PipelineTemplate invalidTemplate = new V2PipelineTemplate();
    invalidTemplate.setId("invalid.id.with.dots"); // Invalid ID with dots
    invalidTemplate.setSchema(V2PipelineTemplate.V2_SCHEMA_VERSION);

    V2PipelineTemplate.Metadata metadata = new V2PipelineTemplate.Metadata();
    metadata.setName("My Template");
    metadata.setDescription("A test template");
    metadata.setScopes(Collections.singletonList("global"));
    invalidTemplate.setMetadata(metadata);

    // Initialize the pipeline field to avoid NullPointerException during serialization
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("application", "myapp");
    invalidTemplate.setPipeline(pipeline);

    String json = objectMapper.writeValueAsString(invalidTemplate);
    String base64Template = Base64.encodeBase64String(json.getBytes());

    Map<String, Object> context = new HashMap<>();
    context.put("pipelineTemplate", base64Template);
    context.put("id", "invalidTemplateId");

    // When and Then
    assertThrows(
        IllegalArgumentException.class,
        () -> task.execute(createStage(context)),
        "Pipeline Template IDs cannot have dots");
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
        PipelineExecutionImpl.newPipeline("test-application"), "updatePipelineTemplate", context);
  }
}
