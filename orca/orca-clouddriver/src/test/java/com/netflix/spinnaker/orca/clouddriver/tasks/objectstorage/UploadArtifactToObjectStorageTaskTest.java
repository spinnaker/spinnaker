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

package com.netflix.spinnaker.orca.clouddriver.tasks.objectstorage;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadArtifactToObjectStorageTaskTest {

  private static final String CLOUD_PROVIDER = "aws";
  private static final String CREDENTIALS = "my-aws-account";
  private static final String REGION = "us-east-1";
  private static final String BUCKET = "my-bucket";
  private static final String OBJECT_KEY_PREFIX = "releases/v1.0.0";
  private static final String ARTIFACT_ID = "artifact-123";

  @Mock private KatoService katoService;
  @Mock private ArtifactUtils artifactUtils;
  @Mock private ObjectMapper objectMapper;

  @SuppressWarnings("rawtypes")
  @Captor
  private ArgumentCaptor<Collection<Map<String, Map>>> operationsCaptor;

  private UploadArtifactToObjectStorageTask task;

  @BeforeEach
  void setUp() {
    task = new UploadArtifactToObjectStorageTask(katoService, artifactUtils, objectMapper);
  }

  @Test
  void shouldSubmitUploadOperationToKato() {
    // Given
    Artifact resolvedArtifact =
        Artifact.builder()
            .type("maven/file")
            .name("my-app-1.0.0.zip")
            .reference("https://artifactory.example.com/my-app-1.0.0.zip")
            .artifactAccount("artifactory-account")
            .build();

    when(artifactUtils.getBoundArtifactForStage(any(), eq(ARTIFACT_ID), any()))
        .thenReturn(resolvedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    assertThat(result.getContext()).containsKey("kato.last.task.id");
    assertThat(result.getContext().get("notification.type"))
        .isEqualTo("uploadArtifactToObjectStorage");

    Map<String, Object> operationDetails = captureOperationDetails(CLOUD_PROVIDER);
    assertThat(operationDetails.get("credentials")).isEqualTo(CREDENTIALS);
    assertThat(operationDetails.get("region")).isEqualTo(REGION);
    assertThat(operationDetails.get("bucket")).isEqualTo(BUCKET);
    assertThat(operationDetails.get("objectKeyPrefix")).isEqualTo(OBJECT_KEY_PREFIX);
    assertThat(operationDetails.get("artifact")).isEqualTo(resolvedArtifact);
  }

  @Test
  void shouldUseDefaultCloudProviderWhenNotSpecified() {
    // Given
    Artifact resolvedArtifact = Artifact.builder().name("app.zip").build();

    when(artifactUtils.getBoundArtifactForStage(any(), eq(ARTIFACT_ID), any()))
        .thenReturn(resolvedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    context.remove("cloudProvider");
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(katoService).requestOperations(eq("aws"), any());
  }

  @Test
  void shouldResolveArtifactFromInlineDefinition() {
    // Given
    Map<String, Object> inlineArtifact = new HashMap<>();
    inlineArtifact.put("type", "embedded/base64");
    inlineArtifact.put("name", "inline-app.zip");
    inlineArtifact.put("reference", "base64encodedcontent");

    Artifact expectedArtifact =
        Artifact.builder()
            .type("embedded/base64")
            .name("inline-app.zip")
            .reference("base64encodedcontent")
            .build();

    when(objectMapper.convertValue(inlineArtifact, Artifact.class)).thenReturn(expectedArtifact);
    when(artifactUtils.getBoundArtifactForStage(any(), eq(null), eq(expectedArtifact)))
        .thenReturn(expectedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    context.remove("artifactId");
    context.put("artifact", inlineArtifact);
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
  }

  @Test
  void shouldHandleEmptyObjectKeyPrefix() {
    // Given
    Artifact resolvedArtifact = Artifact.builder().name("app.zip").build();

    when(artifactUtils.getBoundArtifactForStage(any(), eq(ARTIFACT_ID), any()))
        .thenReturn(resolvedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    context.put("objectKeyPrefix", "");
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);

    Map<String, Object> operationDetails = captureOperationDetails(CLOUD_PROVIDER);
    assertThat(operationDetails.get("objectKeyPrefix")).isEqualTo("");
  }

  @Test
  void shouldSetKatoResultExpected() {
    // Given
    Artifact resolvedArtifact = Artifact.builder().name("app.zip").build();

    when(artifactUtils.getBoundArtifactForStage(any(), eq(ARTIFACT_ID), any()))
        .thenReturn(resolvedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    assertThat(result.getContext().get("kato.result.expected")).isEqualTo(true);
  }

  @Test
  void shouldPassDeleteStaleFilesTrueInPayload() {
    // Given
    Artifact resolvedArtifact = Artifact.builder().name("app.zip").build();

    when(artifactUtils.getBoundArtifactForStage(any(), eq(ARTIFACT_ID), any()))
        .thenReturn(resolvedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    context.put("deleteStaleFiles", true);
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);

    Map<String, Object> operationDetails = captureOperationDetails(CLOUD_PROVIDER);
    assertThat(operationDetails.get("deleteStaleFiles")).isEqualTo(true);
  }

  @Test
  void shouldDefaultDeleteStaleFilesToFalseWhenAbsentFromContext() {
    // Given
    Artifact resolvedArtifact = Artifact.builder().name("app.zip").build();

    when(artifactUtils.getBoundArtifactForStage(any(), eq(ARTIFACT_ID), any()))
        .thenReturn(resolvedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    // deleteStaleFiles is not set in context
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);

    Map<String, Object> operationDetails = captureOperationDetails(CLOUD_PROVIDER);
    assertThat(operationDetails.get("deleteStaleFiles")).isEqualTo(false);
  }

  @Test
  void shouldPassExtractArchiveFalseInPayload() {
    // Given
    Artifact resolvedArtifact = Artifact.builder().name("config.json").build();

    when(artifactUtils.getBoundArtifactForStage(any(), eq(ARTIFACT_ID), any()))
        .thenReturn(resolvedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    context.put("extractArchive", false);
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);

    Map<String, Object> operationDetails = captureOperationDetails(CLOUD_PROVIDER);
    assertThat(operationDetails.get("extractArchive")).isEqualTo(false);
  }

  @Test
  void shouldDefaultExtractArchiveToTrueWhenAbsentFromContext() {
    // Given
    Artifact resolvedArtifact = Artifact.builder().name("app.zip").build();

    when(artifactUtils.getBoundArtifactForStage(any(), eq(ARTIFACT_ID), any()))
        .thenReturn(resolvedArtifact);
    when(katoService.requestOperations(anyString(), any())).thenReturn(new TaskId("kato-task-123"));

    Map<String, Object> context = createStageContext();
    // extractArchive is not set in context
    StageExecutionImpl stage = createStage(context);

    // When
    TaskResult result = task.execute(stage);

    // Then
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);

    Map<String, Object> operationDetails = captureOperationDetails(CLOUD_PROVIDER);
    assertThat(operationDetails.get("extractArchive")).isEqualTo(true);
  }

  private Map<String, Object> createStageContext() {
    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", CLOUD_PROVIDER);
    context.put("credentials", CREDENTIALS);
    context.put("region", REGION);
    context.put("bucket", BUCKET);
    context.put("objectKeyPrefix", OBJECT_KEY_PREFIX);
    context.put("artifactId", ARTIFACT_ID);
    return context;
  }

  private StageExecutionImpl createStage(Map<String, Object> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(PIPELINE, "test-app"), "uploadArtifactToObjectStorage", context);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> captureOperationDetails(String cloudProvider) {
    verify(katoService).requestOperations(eq(cloudProvider), operationsCaptor.capture());

    Collection<Map<String, Map>> operations = operationsCaptor.getValue();
    assertThat(operations).hasSize(1);

    Map<String, Map> operation = operations.iterator().next();
    assertThat(operation).containsKey("uploadArtifactToObjectStorage");

    return (Map<String, Object>) operation.get("uploadArtifactToObjectStorage");
  }
}
