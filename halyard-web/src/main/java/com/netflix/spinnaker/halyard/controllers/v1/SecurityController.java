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
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.config.services.v1.SecurityService;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/security")
public class SecurityController {
  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  SecurityService securityService;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Security> getSecurity(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    DaemonResponse.StaticRequestBuilder<Security> builder = new DaemonResponse.StaticRequestBuilder<>();

    builder.setSeverity(severity);
    builder.setBuildResponse(() -> securityService.getSecurity(deploymentName));

    if (validate) {
      builder.setValidateResponse(() -> securityService.validateSecurity(deploymentName));
    }

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/authn/oauth2", method = RequestMethod.GET)
  DaemonTask<Halconfig, OAuth2> getOAuth2(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    DaemonResponse.StaticRequestBuilder<OAuth2> builder = new DaemonResponse.StaticRequestBuilder<>();

    builder.setSeverity(severity);
    builder.setBuildResponse(() -> securityService.getOAuth2(deploymentName));

    if (validate) {
      builder.setValidateResponse(() -> securityService.validateOAuth2(deploymentName));
    }

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSecurity(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawSecurity) {
    Security security = objectMapper.convertValue(rawSecurity, Security.class);

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setSeverity(severity);
    builder.setUpdate(() -> securityService.setSecurity(deploymentName, security));

    builder.setValidate(ProblemSet::new);
    if (validate) {
      builder.setValidate(() -> securityService.validateSecurity(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/authn/oauth2", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setOAuth2(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawOAuth2) {
    OAuth2 oauth2 = objectMapper.convertValue(rawOAuth2, OAuth2.class);

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setSeverity(severity);
    builder.setUpdate(() -> securityService.setOAuth2(deploymentName, oauth2));

    builder.setValidate(ProblemSet::new);
    if (validate) {
      builder.setValidate(() -> securityService.validateOAuth2(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }
}
