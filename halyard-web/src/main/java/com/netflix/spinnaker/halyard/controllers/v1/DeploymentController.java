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
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.config.services.v1.GenerateService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/config/deployments")
public class DeploymentController {
  @Autowired
  DeploymentService deploymentService;

  @Autowired
  GenerateService generateService;

  @RequestMapping(value = "/{deployment:.+}", method = RequestMethod.GET)
  DaemonResponse<DeploymentConfiguration> deploymentConfiguration(@PathVariable String deployment,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    NodeReference reference = new NodeReference().setDeployment(deployment);
    StaticRequestBuilder<DeploymentConfiguration> builder = new StaticRequestBuilder<>();

    builder.setBuildResponse(() -> deploymentService.getDeploymentConfiguration(reference));

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(reference, severity));
    }

    return builder.build();
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonResponse<List<DeploymentConfiguration>> deploymentConfigurations(
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<List<DeploymentConfiguration>> builder = new StaticRequestBuilder<>();

    builder.setBuildResponse(() -> deploymentService.getAllDeploymentConfigurations());

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateAllDeployments(severity));
    }

    return builder.build();
  }

  @RequestMapping(value = "/{deployment:.+}/generate/", method = RequestMethod.POST)
  DaemonResponse<Void> generateConfig(@PathVariable String deployment) {
    NodeReference reference = new NodeReference().setDeployment(deployment);

    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>();

    builder.setBuildResponse(() -> {
      generateService.generateConfig(reference);
      return null;
    });

    return builder.build();
  }
}
