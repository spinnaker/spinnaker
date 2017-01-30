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
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.DaemonResponse;
import com.netflix.spinnaker.halyard.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Features;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import com.netflix.spinnaker.halyard.config.services.v1.FeaturesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@RestController
@RequestMapping("/v1/config/deployments/{deployment:.+}/features")
public class FeaturesController {
  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  FeaturesService featuresService;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonResponse<Features> getFeatures(
      @PathVariable String deployment,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    NodeFilter filter = new NodeFilter().setDeployment(deployment);

    StaticRequestBuilder<Features> builder = new StaticRequestBuilder<>();

    builder.setBuildResponse(() -> featuresService.getFeatures(filter));

    return builder.build();
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonResponse<Void> setFeatures(
      @PathVariable String deployment,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawFeatures) {
    NodeFilter filter = new NodeFilter().setDeployment(deployment);

    Features features = objectMapper.convertValue(rawFeatures, Features.class);

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> featuresService.setFeatures(filter, features));

    Supplier<ProblemSet> doValidate = ProblemSet::new;

    builder.setValidate(doValidate);
    builder.setHalconfigParser(halconfigParser);

    return builder.build();
  }
}
