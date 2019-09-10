/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Telemetry;
import com.netflix.spinnaker.halyard.config.services.v1.TelemetryService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericEnableDisableRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/telemetry")
@RequiredArgsConstructor
public class TelemetryController {
  private final TelemetryService telemetryService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final HalconfigParser halconfigParser;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Telemetry> getTelemetry(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Telemetry>builder()
        .getter(() -> telemetryService.getTelemetry(deploymentName))
        .validator(() -> telemetryService.validateTelemetry(deploymentName))
        .description("Get telemetry")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setTelemetryEnabled(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(t -> telemetryService.setTelemetryEnabled(deploymentName, false, enabled))
        .validator(() -> telemetryService.validateTelemetry(deploymentName))
        .description("Enable or disable telemetry")
        .build()
        .execute(validationSettings, enabled);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setTelemetry(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Telemetry telemetry) {
    return GenericUpdateRequest.<Telemetry>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(t -> telemetryService.setTelemetry(deploymentName, t))
        .validator(() -> telemetryService.validateTelemetry(deploymentName))
        .description("Edit telemetry settings")
        .build()
        .execute(validationSettings, telemetry);
  }
}
