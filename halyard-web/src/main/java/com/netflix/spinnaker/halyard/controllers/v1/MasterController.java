/*
 * Copyright 2017 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cis;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Master;
import com.netflix.spinnaker.halyard.config.services.v1.MasterService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/ci/{ciName:.+}/masters")
public class MasterController {
  private final MasterService masterService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Master>> masters(@PathVariable String deploymentName,
      @PathVariable String ciName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Master>>builder()
        .getter(() -> masterService.getAllMasters(deploymentName, ciName))
        .validator(() -> masterService.validateAllMasters(deploymentName, ciName))
        .description("Get all masters for " + ciName)
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{masterName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Master> master(@PathVariable String deploymentName,
      @PathVariable String ciName,
      @PathVariable String masterName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Master>builder()
        .getter(() -> masterService.getCiMaster(deploymentName, ciName, masterName))
        .validator(() -> masterService.validateMaster(deploymentName, ciName, masterName))
        .description("Get the " + masterName + " master")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{masterName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteMaster(@PathVariable String deploymentName,
      @PathVariable String ciName,
      @PathVariable String masterName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(() -> masterService.deleteMaster(deploymentName, ciName, masterName))
        .validator(() -> masterService.validateAllMasters(deploymentName, ciName))
        .description("Delete the " + masterName + " master")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{masterName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMaster(@PathVariable String deploymentName,
      @PathVariable String ciName,
      @PathVariable String masterName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawMaster) {
    Master master = objectMapper.convertValue(
        rawMaster,
        Cis.translateMasterType(ciName)
    );
    return GenericUpdateRequest.<Master>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(m -> masterService.setMaster(deploymentName, ciName, masterName, m))
        .validator(() -> masterService.validateMaster(deploymentName, ciName, master.getName()))
        .description("Edit the " + masterName + " master")
        .build()
        .execute(validationSettings, master);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addMaster(@PathVariable String deploymentName,
      @PathVariable String ciName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawMaster) {
    Master master = objectMapper.convertValue(
        rawMaster,
        Cis.translateMasterType(ciName)
    );
    return GenericUpdateRequest.<Master>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(m -> masterService.addMaster(deploymentName, ciName, m))
        .validator(() -> masterService.validateMaster(deploymentName, ciName, master.getName()))
        .description("Add the " + master.getName() + " master")
        .build()
        .execute(validationSettings, master);
  }
}
