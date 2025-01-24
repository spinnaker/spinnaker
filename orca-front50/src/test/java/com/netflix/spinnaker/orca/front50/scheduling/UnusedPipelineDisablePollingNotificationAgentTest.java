/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.orca.front50.scheduling;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import retrofit.RetrofitError;
import retrofit.client.Response;

class UnusedPipelineDisablePollingNotificationAgentTest {

  NotificationClusterLock clusterLock = mock(NotificationClusterLock.class);
  ExecutionRepository executionRepository = mock(ExecutionRepository.class);
  Front50Service front50Service = mock(Front50Service.class);
  Registry registry = mock(Registry.class);
  LongTaskTimer timer = mock(LongTaskTimer.class);

  Id timerId = mock(Id.class);
  Id disabledId = mock(Id.class);

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(registry.createId("pollers.unusedPipelineDisable.timing")).thenReturn(timerId);
    when(registry.createId("pollers.unusedPipelineDisable.disabled")).thenReturn(disabledId);
    when(registry.longTaskTimer(timerId)).thenReturn(timer);
    when(timer.start()).thenReturn(1L);
  }

  @Test
  void disableAppPipelines_shouldDisableUnusedPipelines() {
    Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    UnusedPipelineDisablePollingNotificationAgent agent =
        new UnusedPipelineDisablePollingNotificationAgent(
            clusterLock, executionRepository, front50Service, clock, registry, 3600000, 30, false);
    List<String> orcaExecutionsCount = List.of("pipeline1");
    List<String> front50PipelineConfigIds = List.of("pipeline2");

    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("name", "pipeline2");
    when(front50Service.getPipeline("pipeline2")).thenReturn(pipeline);

    agent.disableAppPipelines("app1", orcaExecutionsCount, front50PipelineConfigIds);

    verify(front50Service, times(1)).getPipeline("pipeline2");
    verify(front50Service, times(1))
        .updatePipeline("pipeline2", Map.of("name", "pipeline2", "disabled", true));
  }

  @Test
  void disableFront50PipelineConfigId_shouldDisablePipeline() {
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("disabled", false);
    Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    UnusedPipelineDisablePollingNotificationAgent agent =
        new UnusedPipelineDisablePollingNotificationAgent(
            clusterLock, executionRepository, front50Service, clock, registry, 3600000, 30, false);
    when(front50Service.getPipeline("pipeline1")).thenReturn(pipeline);

    agent.disableFront50PipelineConfigId("pipeline1");

    verify(front50Service, times(1)).getPipeline("pipeline1");
    verify(front50Service, times(1)).updatePipeline(eq("pipeline1"), any());
  }

  @Test
  void disableAppPipelines_shouldDisableUnusedPipelines_dryRunMode() {
    Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    UnusedPipelineDisablePollingNotificationAgent agent =
        new UnusedPipelineDisablePollingNotificationAgent(
            clusterLock, executionRepository, front50Service, clock, registry, 3600000, 30, true);
    List<String> orcaExecutionsCount = List.of("pipeline1");
    List<String> front50PipelineConfigIds = List.of("pipeline2");

    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("name", "pipeline2");
    when(front50Service.getPipeline("pipeline2")).thenReturn(pipeline);

    agent.disableAppPipelines("app1", orcaExecutionsCount, front50PipelineConfigIds);

    verify(front50Service, never()).getPipeline("pipeline2");
    verify(front50Service, never())
        .updatePipeline("pipeline2", Map.of("name", "pipeline2", "disabled", true));
  }

  @Test
  void disableFront50PipelineConfigId_shouldNotDisableAlreadyDisabledPipeline() {
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("disabled", true);
    when(front50Service.getPipeline("pipeline1")).thenReturn(pipeline);
    Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    UnusedPipelineDisablePollingNotificationAgent agent =
        new UnusedPipelineDisablePollingNotificationAgent(
            clusterLock, executionRepository, front50Service, clock, registry, 3600000, 30, false);
    agent.disableFront50PipelineConfigId("pipeline1");

    verify(front50Service, times(1)).getPipeline("pipeline1");
    verify(front50Service, never()).updatePipeline(eq("pipeline1"), any());
  }

  @Test
  void tick_shouldEvaluateAllApplications() {
    when(executionRepository.retrieveAllApplicationNames(PIPELINE))
        .thenReturn(List.of("app1", "app2"));
    when(front50Service.getPipelines(anyString(), eq(false), eq(true)))
        .thenReturn(List.of(Map.of("id", "pipeline1")));
    when(disabledId.withTag(any())).thenReturn(disabledId);

    Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    UnusedPipelineDisablePollingNotificationAgent agent =
        new UnusedPipelineDisablePollingNotificationAgent(
            clusterLock, executionRepository, front50Service, clock, registry, 3600000, 30, false);
    agent.tick();

    verify(executionRepository, times(1)).retrieveAllApplicationNames(PIPELINE);
    verify(front50Service, times(2)).getPipelines(anyString(), eq(false), eq(true));
  }

  @Test
  void tick_shouldHandleNoApplications() {
    when(executionRepository.retrieveAllApplicationNames(PIPELINE))
        .thenReturn(Collections.emptyList());
    Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    UnusedPipelineDisablePollingNotificationAgent agent =
        new UnusedPipelineDisablePollingNotificationAgent(
            clusterLock, executionRepository, front50Service, clock, registry, 3600000, 30, false);
    agent.tick();

    verify(executionRepository, times(1)).retrieveAllApplicationNames(PIPELINE);
    verify(front50Service, never()).getPipelines(anyString(), eq(false), eq(true));
  }

  @Test
  void disableFront50PipelineConfigId_shouldLogWarningFor404() {
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("disabled", false);
    Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    UnusedPipelineDisablePollingNotificationAgent agent =
        new UnusedPipelineDisablePollingNotificationAgent(
            clusterLock, executionRepository, front50Service, clock, registry, 3600000, 30, false);
    when(front50Service.getPipeline("pipeline1")).thenReturn(pipeline);
    doThrow(
            new SpinnakerHttpException(
                RetrofitError.httpError(
                    "http://front50",
                    new Response("http://front50", 404, "", List.of(), null),
                    null,
                    null)))
        .when(front50Service)
        .updatePipeline(eq("pipeline1"), any());

    agent.disableFront50PipelineConfigId("pipeline1");

    verify(front50Service, times(1)).getPipeline("pipeline1");
    verify(front50Service, times(1)).updatePipeline(eq("pipeline1"), any());
  }

  @Test
  void disableFront50PipelineConfigId_shouldThrowExceptionForOtherErrors() {
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("disabled", false);
    Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    UnusedPipelineDisablePollingNotificationAgent agent =
        new UnusedPipelineDisablePollingNotificationAgent(
            clusterLock, executionRepository, front50Service, clock, registry, 3600000, 30, false);
    when(front50Service.getPipeline("pipeline1")).thenReturn(pipeline);
    doThrow(
            new SpinnakerHttpException(
                RetrofitError.httpError(
                    "http://front50",
                    new Response("http://front50", 500, "", List.of(), null),
                    null,
                    null)))
        .when(front50Service)
        .updatePipeline(eq("pipeline1"), any());

    assertThrows(
        SpinnakerHttpException.class, () -> agent.disableFront50PipelineConfigId("pipeline1"));

    verify(front50Service, times(1)).getPipeline("pipeline1");
    verify(front50Service, times(1)).updatePipeline(eq("pipeline1"), any());
  }
}
