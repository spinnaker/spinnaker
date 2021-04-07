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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPages;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ProcessesService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ScaleProcess;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.UpdateProcess;
import groovy.util.logging.Slf4j;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@Slf4j
@RequiredArgsConstructor
public class Processes {

  private final ProcessesService api;

  public List<Process> getAllProcessesByAppId(String appGuid) {
    if (appGuid == null || appGuid.isEmpty()) {
      throw new IllegalArgumentException(
          "An application guid must be provided in order to return processes by app.");
    }
    return collectPages("processes", page -> api.getProcesses(page, appGuid));
  }

  public void scaleProcess(
      String guid,
      @Nullable Integer instances,
      @Nullable Integer memInMb,
      @Nullable Integer diskInMb)
      throws CloudFoundryApiException {
    if ((memInMb == null && diskInMb == null && instances == null)
        || (Integer.valueOf(0).equals(memInMb)
            && Integer.valueOf(0).equals(diskInMb)
            && Integer.valueOf(0).equals(instances))) {
      return;
    }
    safelyCall(() -> api.scaleProcess(guid, new ScaleProcess(instances, memInMb, diskInMb)));
  }

  public Optional<Process> findProcessById(String guid) {
    return safelyCall(() -> api.findProcessById(guid));
  }

  public void updateProcess(
      String guid,
      @Nullable String command,
      @Nullable String healthCheckType,
      @Nullable String healthCheckEndpoint)
      throws CloudFoundryApiException {
    final Process.HealthCheck healthCheck =
        healthCheckType != null ? new Process.HealthCheck().setType(healthCheckType) : null;
    if (healthCheckEndpoint != null && !healthCheckEndpoint.isEmpty() && healthCheck != null) {
      healthCheck.setData(new Process.HealthCheckData().setEndpoint(healthCheckEndpoint));
    }
    if (command != null && command.isEmpty()) {
      throw new IllegalArgumentException(
          "Buildpack commands cannot be empty. Please specify a custom command or set it to null to use the original buildpack command.");
    }

    safelyCall(() -> api.updateProcess(guid, new UpdateProcess(command, healthCheck)));
  }

  @Nullable
  public Optional<ProcessStats.State> getProcessState(String guid) throws CloudFoundryApiException {
    return safelyCall(() -> api.findProcessStatsById(guid))
        .map(pr -> pr.getResources().stream().findAny().map(ProcessStats::getState))
        .orElse(Optional.empty());
  }
}
