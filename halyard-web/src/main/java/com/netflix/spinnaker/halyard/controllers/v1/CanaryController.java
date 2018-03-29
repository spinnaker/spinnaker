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
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.CanaryAccountService;
import com.netflix.spinnaker.halyard.config.services.v1.CanaryService;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.function.Supplier;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/canary")
public class CanaryController {

  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  CanaryService canaryService;

  @Autowired
  CanaryAccountService canaryAccountService;

  @Autowired
  HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Canary> getCanary(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    DaemonResponse.StaticRequestBuilder<Canary> builder = new DaemonResponse.StaticRequestBuilder<>(
        () -> canaryService.getCanary(deploymentName));

    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> canaryService.validateCanary(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get all canary settings");
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setCanary(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawCanary) {
    Canary canary = objectMapper.convertValue(rawCanary, Canary.class);

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> canary.stageLocalFiles(configPath));
    builder.setSeverity(severity);
    builder.setUpdate(() -> canaryService.setCanary(deploymentName, canary));

    builder.setValidate(ProblemSet::new);

    if (validate) {
      builder.setValidate(() -> canaryService.validateCanary(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit canary analysis settings");
  }

  @RequestMapping(value = "/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> canaryService.setCanaryEnabled(deploymentName, enabled));
    builder.setSeverity(severity);

    builder.setValidate(ProblemSet::new);

    if (validate) {
      builder.setValidate(() -> canaryService.validateCanary(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit canary settings");
  }

  @RequestMapping(value = "/{serviceIntegrationName:.+}/accounts/account/{accountName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, AbstractCanaryAccount> getCanaryAccount(
      @PathVariable String deploymentName,
      @PathVariable String serviceIntegrationName,
      @PathVariable String accountName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    DaemonResponse.StaticRequestBuilder<AbstractCanaryAccount> builder = new DaemonResponse.StaticRequestBuilder<>(
        () -> canaryAccountService.getCanaryAccount(deploymentName, serviceIntegrationName, accountName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> canaryService.validateCanary(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get " + accountName + " canary account");
  }

  @RequestMapping(value = "/{serviceIntegrationName:.+}/accounts/account/{accountName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setCanaryAccount(
      @PathVariable String deploymentName,
      @PathVariable String serviceIntegrationName,
      @PathVariable String accountName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawCanaryAccount) {
    AbstractCanaryAccount canaryAccount = objectMapper.convertValue(
        rawCanaryAccount,
        Canary.translateCanaryAccountType(serviceIntegrationName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> canaryAccount.stageLocalFiles(configPath));
    builder.setUpdate(() -> canaryAccountService.setAccount(deploymentName, serviceIntegrationName, accountName, canaryAccount));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;

    if (validate) {
      doValidate = () -> canaryService.validateCanary(deploymentName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + accountName + " canary account");
  }

  @RequestMapping(value = "/{serviceIntegrationName:.+}/accounts/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addCanaryAccount(
      @PathVariable String deploymentName,
      @PathVariable String serviceIntegrationName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawCanaryAccount) {
    AbstractCanaryAccount canaryAccount = objectMapper.convertValue(
        rawCanaryAccount,
        Canary.translateCanaryAccountType(serviceIntegrationName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> canaryAccount.stageLocalFiles(configPath));
    builder.setSeverity(severity);
    builder.setUpdate(() -> canaryAccountService.addAccount(deploymentName, serviceIntegrationName, canaryAccount));

    Supplier<ProblemSet> doValidate = ProblemSet::new;

    if (validate) {
      doValidate = () -> canaryService.validateCanary(deploymentName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Add the " + canaryAccount.getName() + " canary account to " + serviceIntegrationName + " service integration");
  }

  @RequestMapping(value = "/{serviceIntegrationName:.+}/accounts/account/{accountName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteCanaryAccount(
      @PathVariable String deploymentName,
      @PathVariable String serviceIntegrationName,
      @PathVariable String accountName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> canaryAccountService.deleteAccount(deploymentName, serviceIntegrationName, accountName));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;

    if (validate) {
      doValidate = () -> canaryService.validateCanary(deploymentName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Delete the " + accountName + " canary account");
  }
}
