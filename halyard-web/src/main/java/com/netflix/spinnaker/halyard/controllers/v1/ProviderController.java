/*
 * Copyright 2016 Google, Inc.
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
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.services.v1.ProviderService;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers")
public class ProviderController {
  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  ProviderService providerService;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/{providerName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Provider> get(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<Provider> builder = new StaticRequestBuilder<>();

    builder.setBuildResponse(() -> providerService.getProvider(deploymentName, providerName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> providerService.validateProvider(deploymentName, providerName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get the " + providerName + " provider");
  }

  @RequestMapping(value = "/{providerName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setProvider(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawProvider) {
    Provider provider = objectMapper.convertValue(
        rawProvider,
        Providers.translateProviderType(providerName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> providerService.setProvider(deploymentName, provider));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> providerService.validateProvider(deploymentName, providerName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + providerName + " provider");
  }

  @RequestMapping(value = "/{providerName:.+}/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> providerService.setEnabled(deploymentName, providerName, enabled));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> providerService.validateProvider(deploymentName, providerName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + providerName + " provider");
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Provider>> providers(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<List<Provider>> builder = new StaticRequestBuilder<>();

    builder.setBuildResponse(() -> providerService.getAllProviders(deploymentName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> providerService.validateAllProviders(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get all providers");
  }
}
