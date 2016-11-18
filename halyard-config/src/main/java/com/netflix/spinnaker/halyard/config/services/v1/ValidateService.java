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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.HalconfigCoordinates;
import com.netflix.spinnaker.halyard.config.model.v1.HalconfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.Validatable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ValidateService {
  @Autowired
  DeploymentService deploymentService;

  public void validate(Validatable validatable, HalconfigCoordinates coordinates) {
    DeploymentConfiguration deployment = deploymentService.getDeploymentConfiguration(coordinates);
    HalconfigProblemSetBuilder builder = new HalconfigProblemSetBuilder().setCoordinates(coordinates);

    validatable.validate(builder, deployment);

    builder.build().throwIfProblem();
  }
}
