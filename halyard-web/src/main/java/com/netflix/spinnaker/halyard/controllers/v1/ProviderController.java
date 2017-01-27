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

import com.netflix.spinnaker.halyard.DaemonResponse;
import com.netflix.spinnaker.halyard.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import com.netflix.spinnaker.halyard.config.services.v1.ProviderService;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/config/deployments/{deployment:.+}/providers")
public class ProviderController {
  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  ProviderService providerService;

  @RequestMapping(value = "/{provider:.+}", method = RequestMethod.GET)
  DaemonResponse<Provider> provider(
      @PathVariable String deployment,
      @PathVariable String provider,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    NodeFilter filter = new NodeFilter()
        .setDeployment(deployment)
        .setProvider(provider);

    StaticRequestBuilder<Provider> builder = new StaticRequestBuilder<>();

    builder.setBuildResponse(() -> providerService.getProvider(filter));

    if (validate) {
      builder.setValidateResponse(() -> providerService.validateProvider(filter, severity));
    }

    return builder.build();
  }

  @RequestMapping(value = "/{provider:.+}/enabled", method = RequestMethod.PUT)
  DaemonResponse<Void> setEnabled(
      @PathVariable String deployment,
      @PathVariable String provider,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody boolean enabled) {
    NodeFilter filter = new NodeFilter()
        .setDeployment(deployment)
        .setProvider(provider);

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> providerService.setEnabled(filter, enabled));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> providerService.validateProvider(filter, severity);
    }

    builder.setValidate(doValidate);
    builder.setHalconfigParser(halconfigParser);

    return builder.build();
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonResponse<List<Provider>> providers(@PathVariable String deployment,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    NodeFilter filter = new NodeFilter().setDeployment(deployment);
    StaticRequestBuilder<List<Provider>> builder = new StaticRequestBuilder<>();

    builder.setBuildResponse(() -> providerService.getAllProviders(filter));

    if (validate) {
      builder.setValidateResponse(() -> providerService.validateProvider(filter, severity));
    }

    return builder.build();
  }
}
