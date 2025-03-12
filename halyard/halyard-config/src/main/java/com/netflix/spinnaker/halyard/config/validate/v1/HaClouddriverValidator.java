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
public class HaClouddriverValidator extends Validator<DeploymentEnvironment> {

  @Override
  public void validate(ConfigProblemSetBuilder p, DeploymentEnvironment n) {
    CustomSizing customSizing = n.getCustomSizing();
    HaServices haServices = n.getHaServices();

    boolean haCloudDriverEnabled = haServices.getClouddriver().isEnabled();
    if (haCloudDriverEnabled && customSizing.hasCustomSizing("spin-clouddriver")) {
      p.addProblem(
          Problem.Severity.WARNING,
          "High Availability (HA) is enabled for clouddriver, but found custom sizing for the main service (this setting will be ignored). "
              + "With HA enabled, the service is split into multiple sub-services (eg. clouddriver-caching, clouddriver-rw). You need to update the component sizing for each sub-service, individually.");
    }

    if (!haCloudDriverEnabled
        && (customSizing.hasCustomSizing("spin-clouddriver-rw")
            || customSizing.hasCustomSizing("spin-clouddriver-ro")
            || customSizing.hasCustomSizing("spin-clouddriver-ro-deck")
            || customSizing.hasCustomSizing("spin-clouddriver-caching"))) {
      p.addProblem(
          Problem.Severity.WARNING,
          "Discovered custom sizing for HA clouddriver subcomponent, but High Availability (HA) is not enabled. Please enable HA or edit the clouddriver main service directly.");
    }
  }
}
