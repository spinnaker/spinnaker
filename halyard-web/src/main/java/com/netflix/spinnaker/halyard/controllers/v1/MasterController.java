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
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Master;
import com.netflix.spinnaker.halyard.config.model.v1.node.Webhooks;
import com.netflix.spinnaker.halyard.config.services.v1.MasterService;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/webhooks/{webhookName:.+}/masters")
public class MasterController {
  @Autowired
  MasterService masterService;

  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Master>> masters(@PathVariable String deploymentName, @PathVariable String webhookName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<List<Master>> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> masterService.getAllMasters(deploymentName, webhookName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> masterService.validateAllMasters(deploymentName, webhookName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get all masters for " + webhookName);
  }

  @RequestMapping(value = "/{masterName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Master> master(
      @PathVariable String deploymentName,
      @PathVariable String webhookName,
      @PathVariable String masterName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<Master> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> masterService.getWebhookMaster(deploymentName, webhookName, masterName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> masterService.validateMaster(deploymentName, webhookName, masterName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get the " + masterName + " master");
  }

  @RequestMapping(value = "/{masterName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteMaster(
      @PathVariable String deploymentName,
      @PathVariable String webhookName,
      @PathVariable String masterName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> masterService.deleteMaster(deploymentName, webhookName, masterName));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> masterService.validateAllMasters(deploymentName, webhookName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Delete the " + masterName + " master");
  }

  @RequestMapping(value = "/{masterName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMaster(
      @PathVariable String deploymentName,
      @PathVariable String webhookName,
      @PathVariable String masterName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawMaster) {
    Master master = objectMapper.convertValue(
        rawMaster,
        Webhooks.translateMasterType(webhookName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> masterService.setMaster(deploymentName, webhookName, masterName, master));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> masterService.validateMaster(deploymentName, webhookName, master.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + masterName + " master");
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addMaster(
      @PathVariable String deploymentName,
      @PathVariable String webhookName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawMaster) {
    Master master = objectMapper.convertValue(
        rawMaster,
        Webhooks.translateMasterType(webhookName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();
    builder.setSeverity(severity);

    builder.setUpdate(() -> masterService.addMaster(deploymentName, webhookName, master));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> masterService.validateMaster(deploymentName, webhookName, master.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Add the " + master.getName() + " master");
  }
}
