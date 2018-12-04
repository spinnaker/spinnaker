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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.CiService;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.models.v1.DefaultValidationSettings;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/ci")
public class CiController {
  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  CiService ciService;

  @RequestMapping(value = "/{ciName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Ci> ci(@PathVariable String deploymentName,
      @PathVariable String ciName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Ci>builder()
        .getter(() -> ciService.getCi(deploymentName, ciName))
        .validator(() -> ciService.validateCi(deploymentName, ciName))
        .description("Get " + ciName + " ci")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{ciName:.+}/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(
      @PathVariable String deploymentName,
      @PathVariable String ciName,
      @RequestParam(required = false, defaultValue = DefaultValidationSettings.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultValidationSettings.severity) Severity severity,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> ciService.setEnabled(deploymentName, ciName, enabled));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> ciService.validateCi(deploymentName, ciName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit " + ciName + " settings");
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Ci>> cis(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Ci>>builder()
        .getter(() -> ciService.getAllCis(deploymentName))
        .validator(() -> ciService.validateAllCis(deploymentName))
        .description("Get all Continuous Integration services")
        .build()
        .execute(validationSettings);
  }
}
