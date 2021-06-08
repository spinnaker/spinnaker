/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.clouddriver.KatoRestService;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

@RunWith(JUnitPlatform.class)
public final class WaitOnJobCompletionTest {
  @Test
  void jobTimeoutSpecifiedByRunJobTask() {
    Duration duration = Duration.ofMinutes(10);

    WaitOnJobCompletion task = new WaitOnJobCompletion();
    StageExecutionImpl myStage =
        createStageWithContext(ImmutableMap.of("jobRuntimeLimit", duration.toString()));
    assertThat(task.getDynamicTimeout(myStage))
        .isEqualTo((duration.plus(WaitOnJobCompletion.getPROVIDER_PADDING())).toMillis());

    StageExecutionImpl myStageInvalid =
        createStageWithContext(ImmutableMap.of("jobRuntimeLimit", "garbage"));
    assertThat(task.getDynamicTimeout(myStageInvalid)).isEqualTo(task.getTimeout());
  }

  @Test
  void taskSearchJobByApplicationUsingContextApplication() {
    KatoRestService mockKatoRestService = mock(KatoRestService.class);
    Front50Service mockFront50Service = mock(Front50Service.class);
    RetrySupport retrySupport = new RetrySupport();
    ObjectMapper objectMapper = new ObjectMapper();

    WaitOnJobCompletion task = new WaitOnJobCompletion();

    ReflectionTestUtils.setField(task, "retrySupport", retrySupport);
    ReflectionTestUtils.setField(task, "objectMapper", objectMapper);
    ReflectionTestUtils.setField(task, "katoRestService", mockKatoRestService);
    ReflectionTestUtils.setField(task, "front50Service", mockFront50Service);

    Response mockResponse =
        new Response(
            "test-url",
            200,
            "test-reason",
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"jobState\": \"Succeeded\"}".getBytes()));

    when(mockKatoRestService.collectJob(any(), any(), any(), any())).thenReturn(mockResponse);

    StageExecutionImpl myStage =
        createStageWithContext(
            ImmutableMap.of(
                "application",
                "context-app",
                "deploy.jobs",
                ImmutableMap.of("test", ImmutableList.of("job test"))));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(mockKatoRestService, times(1)).collectJob(eq("context-app"), any(), any(), any());
    verify(mockFront50Service, times(0)).get(any());
  }

  @Test
  void taskSearchJobByApplicationUsingContextMoniker() {
    KatoRestService mockKatoRestService = mock(KatoRestService.class);
    RetrySupport retrySupport = new RetrySupport();
    ObjectMapper objectMapper = new ObjectMapper();
    Front50Service mockFront50Service = mock(Front50Service.class);

    WaitOnJobCompletion task = new WaitOnJobCompletion();

    ReflectionTestUtils.setField(task, "retrySupport", retrySupport);
    ReflectionTestUtils.setField(task, "objectMapper", objectMapper);
    ReflectionTestUtils.setField(task, "katoRestService", mockKatoRestService);
    ReflectionTestUtils.setField(task, "front50Service", mockFront50Service);

    Response mockResponse =
        new Response(
            "test-url",
            200,
            "test-reason",
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"jobState\": \"Succeeded\"}".getBytes()));

    when(mockKatoRestService.collectJob(any(), any(), any(), any())).thenReturn(mockResponse);

    StageExecutionImpl myStage =
        createStageWithContext(
            ImmutableMap.of(
                "moniker", ImmutableMap.of("app", "moniker-app"),
                "application", "context-app",
                "deploy.jobs", ImmutableMap.of("test", ImmutableList.of("job test"))));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(mockKatoRestService, times(1)).collectJob(eq("moniker-app"), any(), any(), any());
    verify(mockFront50Service, times(0)).get(any());
  }

  @Test
  void taskSearchJobByApplicationUsingParsedName() {
    KatoRestService mockKatoRestService = mock(KatoRestService.class);
    RetrySupport retrySupport = new RetrySupport();
    ObjectMapper objectMapper = new ObjectMapper();
    Front50Service mockFront50Service = mock(Front50Service.class);

    WaitOnJobCompletion task = new WaitOnJobCompletion();

    ReflectionTestUtils.setField(task, "retrySupport", retrySupport);
    ReflectionTestUtils.setField(task, "objectMapper", objectMapper);
    ReflectionTestUtils.setField(task, "katoRestService", mockKatoRestService);
    ReflectionTestUtils.setField(task, "front50Service", mockFront50Service);

    Response mockResponse =
        new Response(
            "test-url",
            200,
            "test-reason",
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"jobState\": \"Succeeded\"}".getBytes()));

    when(mockKatoRestService.collectJob(any(), any(), any(), any())).thenReturn(mockResponse);
    when(mockFront50Service.get(any())).thenReturn(new Application("atest"));

    StageExecutionImpl myStage =
        createStageWithContextWithoutExecutionApplication(
            ImmutableMap.of(
                "deploy.jobs", ImmutableMap.of("test", ImmutableList.of("atest-btest-ctest"))));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(mockKatoRestService, times(1)).collectJob(eq("atest"), any(), any(), any());
    verify(mockFront50Service, times(1)).get(eq("atest"));
  }

  @Test
  void taskSearchJobByApplicationUsingExecutionApp() {
    KatoRestService mockKatoRestService = mock(KatoRestService.class);
    RetrySupport retrySupport = new RetrySupport();
    ObjectMapper objectMapper = new ObjectMapper();
    Front50Service mockFront50Service = mock(Front50Service.class);

    WaitOnJobCompletion task = new WaitOnJobCompletion();

    ReflectionTestUtils.setField(task, "retrySupport", retrySupport);
    ReflectionTestUtils.setField(task, "objectMapper", objectMapper);
    ReflectionTestUtils.setField(task, "katoRestService", mockKatoRestService);
    ReflectionTestUtils.setField(task, "front50Service", mockFront50Service);

    Response mockResponse =
        new Response(
            "test-url",
            200,
            "test-reason",
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"jobState\": \"Succeeded\"}".getBytes()));

    when(mockKatoRestService.collectJob(any(), any(), any(), any())).thenReturn(mockResponse);

    StageExecutionImpl myStage =
        createStageWithContext(
            ImmutableMap.of(
                "deploy.jobs", ImmutableMap.of("test", ImmutableList.of("atest-btest-ctest"))));

    TaskResult result = task.execute(myStage);
    AssertionsForClassTypes.assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    verify(mockKatoRestService, times(1)).collectJob(eq("test-app"), any(), any(), any());
    verify(mockFront50Service, times(0)).get(any());
  }

  private StageExecutionImpl createStageWithContext(Map<String, ?> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test-app"),
        "test",
        new HashMap<>(context));
  }

  private StageExecutionImpl createStageWithContextWithoutExecutionApplication(
      Map<String, ?> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, null), "test", new HashMap<>(context));
  }
}
