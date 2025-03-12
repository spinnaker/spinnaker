/*
 * Copyright 2019 Armory, Inc.
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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Stats;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatsService {
  private final LookupService lookupService;
  private final ValidateService validateService;
  private final DeploymentService deploymentService;

  public Stats getStats(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setStats();

    return lookupService.getSingularNodeOrDefault(
        filter, Stats.class, Stats::new, n -> setStats(deploymentName, n));
  }

  public void setStats(String deploymentName, Stats stats) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setStats(stats);
  }

  public void setStatsEnabled(String deploymentName, boolean validate, boolean enable) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Stats stats = deploymentConfiguration.getStats();
    stats.setEnabled(enable);
  }

  public ProblemSet validateStats(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setStats();
    return validateService.validateMatchingFilter(filter);
  }
}
