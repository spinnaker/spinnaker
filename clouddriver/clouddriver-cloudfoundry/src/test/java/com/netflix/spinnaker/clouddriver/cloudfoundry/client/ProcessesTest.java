/*
 * Copyright 2021 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ProcessesService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessResources;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.UpdateProcess;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import retrofit2.Response;
import retrofit2.mock.Calls;

public class ProcessesTest {
  private final ProcessesService processesService = mock(ProcessesService.class);
  private final Processes processes = new Processes(processesService);

  @Test
  void dontScaleApplicationIfInputsAreNullOrZero() {
    processes.scaleProcess("id", null, null, null);
    processes.scaleProcess("id", 0, 0, 0);

    verify(processesService, never()).scaleProcess(any(), any());
  }

  @Test
  void scaleApplicationIfInputsAreMixOfNullAndZero() {
    when(processesService.scaleProcess(any(), any()))
        .thenReturn(Calls.response(Response.success(null)));

    processes.scaleProcess("id", 0, null, null);

    verify(processesService).scaleProcess(any(), any());
  }

  @Test
  void updateProcess() {
    when(processesService.updateProcess(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(new Process())));

    processes.updateProcess("guid1", "command1", "http", "/endpoint", 180, 180);
    verify(processesService)
        .updateProcess(
            "guid1",
            new UpdateProcess(
                "command1",
                new Process.HealthCheck.HealthCheckBuilder()
                    .type("http")
                    .data(
                        new Process.HealthCheckData.HealthCheckDataBuilder()
                            .endpoint("/endpoint")
                            .invocationTimeout(180)
                            .timeout(180)
                            .build())
                    .build()));

    processes.updateProcess("guid1", "command1", "http", null, null, null);
    verify(processesService)
        .updateProcess(
            "guid1",
            new UpdateProcess(
                "command1",
                new Process.HealthCheck.HealthCheckBuilder()
                    .type("http")
                    .data(new Process.HealthCheckData.HealthCheckDataBuilder().build())
                    .build()));

    processes.updateProcess("guid1", "command1", "http", "/endpoint", 180, null);
    verify(processesService)
        .updateProcess(
            "guid1",
            new UpdateProcess(
                "command1",
                new Process.HealthCheck.HealthCheckBuilder()
                    .type("http")
                    .data(
                        new Process.HealthCheckData.HealthCheckDataBuilder()
                            .endpoint("/endpoint")
                            .timeout(180)
                            .build())
                    .build()));
    processes.updateProcess("guid1", "command1", "http", "/endpoint", null, 180);
    verify(processesService)
        .updateProcess(
            "guid1",
            new UpdateProcess(
                "command1",
                new Process.HealthCheck.HealthCheckBuilder()
                    .type("http")
                    .data(
                        new Process.HealthCheckData.HealthCheckDataBuilder()
                            .endpoint("/endpoint")
                            .invocationTimeout(180)
                            .build())
                    .build()));
  }

  @Test
  void getProcessState() {
    ProcessStats processStats = new ProcessStats().setState(ProcessStats.State.RUNNING);
    ProcessResources processResources =
        new ProcessResources().setResources(Collections.singletonList(processStats));
    when(processesService.findProcessStatsById(anyString()))
        .thenReturn(Calls.response(Response.success(processResources)));
    ProcessStats.State result = processes.getProcessState("some-app-guid").get();
    assertThat(result).isEqualTo(ProcessStats.State.RUNNING);
  }

  @Test
  void updateProcessHealthCheck1() {
    when(processesService.updateProcess(any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(new Process())));

    processes.updateProcess("guid1", null, null, null, 90, null);
    verify(processesService)
        .updateProcess(
            "guid1",
            new UpdateProcess(
                null,
                new Process.HealthCheck.HealthCheckBuilder()
                    .type(null)
                    .data(
                        new Process.HealthCheckData.HealthCheckDataBuilder()
                            .endpoint(null)
                            .invocationTimeout(null)
                            .timeout(90)
                            .build())
                    .build()));
  }
}
