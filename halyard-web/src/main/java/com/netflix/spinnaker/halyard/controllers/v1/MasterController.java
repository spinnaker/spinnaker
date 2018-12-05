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
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

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
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> masterService.deleteMaster(deploymentName, ciName, masterName));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> masterService.validateAllMasters(deploymentName, ciName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Delete the " + masterName + " master");
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

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> master.stageLocalFiles(configPath));
    builder.setUpdate(() -> masterService.setMaster(deploymentName, ciName, masterName, master));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> masterService.validateMaster(deploymentName, ciName, master.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + masterName + " master");
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

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> master.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> masterService.addMaster(deploymentName, ciName, master));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> masterService.validateMaster(deploymentName, ciName, master.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Add the " + master.getName() + " master");
  }
}
