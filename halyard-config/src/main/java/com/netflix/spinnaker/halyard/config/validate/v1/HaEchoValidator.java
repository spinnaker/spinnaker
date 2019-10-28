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

package com.netflix.spinnaker.halyard.config.validate.v1;

import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.node.CustomSizing;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.stereotype.Component;

@Component
public class HaEchoValidator extends Validator<DeploymentEnvironment> {

  @Override
  public void validate(ConfigProblemSetBuilder p, DeploymentEnvironment n) {
    CustomSizing customSizing = n.getCustomSizing();
    HaServices haServices = n.getHaServices();

    boolean haEchoEnabled = haServices.getEcho().isEnabled();
    if (haEchoEnabled && customSizing.hasCustomSizing("spin-echo")) {
      p.addProblem(
          Problem.Severity.WARNING,
          "High Availability (HA) is enabled for echo, but found custom sizing for the main service (this setting will be ignored). "
              + "With HA enabled, the service is split into multiple sub-services (spin-echo-scheduler, spin-echo-worker). You need to update the component sizing for each sub-service, individually.");
    }

    if (!haEchoEnabled
        && (customSizing.hasCustomSizing("spin-echo-worker")
            || customSizing.hasCustomSizing("spin-echo-scheduler"))) {
      p.addProblem(
          Problem.Severity.WARNING,
          "Discovered custom sizing for HA echo subcomponent, but High Availability (HA) is not enabled. Please enable HA or edit the echo main service directly.");
    }
  }
}
