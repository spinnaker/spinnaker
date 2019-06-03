/*
 * Copyright 2018 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.HaServiceService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericEnableDisableRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/deploymentEnvironment/haServices")
public class HaServiceController {
  private final HalconfigParser halconfigParser;
  private final HaServiceService haServiceService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/{serviceName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, HaService> get(
      @PathVariable String deploymentName,
      @PathVariable String serviceName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<HaService>builder()
        .getter(() -> haServiceService.getHaService(deploymentName, serviceName))
        .validator(() -> haServiceService.validateHaService(deploymentName, serviceName))
        .description("Get the " + serviceName + " high availability service configuration")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{serviceName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setHaService(
      @PathVariable String deploymentName,
      @PathVariable String serviceName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawHaService) {
    HaService haService =
        objectMapper.convertValue(rawHaService, HaServices.translateHaServiceType(serviceName));
    return GenericUpdateRequest.<HaService>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(h -> haServiceService.setHaService(deploymentName, h))
        .validator(() -> haServiceService.validateHaService(deploymentName, serviceName))
        .description("Edit the " + serviceName + " high availability service configuration")
        .build()
        .execute(validationSettings, haService);
  }

  @RequestMapping(value = "/{serviceName:.+}/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(
      @PathVariable String deploymentName,
      @PathVariable String serviceName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> haServiceService.setEnabled(deploymentName, serviceName, e))
        .validator(() -> haServiceService.validateHaService(deploymentName, serviceName))
        .description("Edit the " + serviceName + " high availability service configuration")
        .build()
        .execute(validationSettings, enabled);
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<HaService>> haServices(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<HaService>>builder()
        .getter(() -> haServiceService.getAllHaServices(deploymentName))
        .validator(() -> haServiceService.validateAllHaServices(deploymentName))
        .description("Get all high availability service configurations")
        .build()
        .execute(validationSettings);
  }
}
