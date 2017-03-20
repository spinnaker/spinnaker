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

package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceInterfaceFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;

/**
 * A Deployment is a running Spinnaker installation.
 */
abstract public class Deployment {
  @Setter
  ServiceInterfaceFactory serviceInterfaceFactory;

  abstract public DeploymentType deploymentType();

  /**
   * Get details about a running Spinnaker service.
   * @param service is service being contacted for details.
   * @return details about the state of the running service.
   */
  abstract public RunningServiceDetails getServiceDetails(SpinnakerService service);

  /**
   * The endpoint format is specific to a provider/deployment pair.
   *
   * @return The endpoints that each spinnaker service are reachable on.
   */
  abstract public SpinnakerEndpoints getEndpoints();

  /**
   * Deploy a fresh install of Spinnaker. This will fail if Spinnaker is already
   * running.
   */
  abstract public RemoteAction deploy(String spinnakerOutputPath);

  /**
   * Install Spinnaker w/o config. This generally only work on local deployments.
   */
  abstract public RemoteAction install(String spinnakerOutputPath);
}
