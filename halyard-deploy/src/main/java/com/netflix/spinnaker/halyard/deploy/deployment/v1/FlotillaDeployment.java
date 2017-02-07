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

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import com.netflix.spinnaker.halyard.deploy.provider.v1.ProviderInterface;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.EndpointType;

abstract public class FlotillaDeployment<T extends Account> extends Deployment {
  public FlotillaDeployment(AccountDeploymentDetails<T> deploymentDetails, ProviderInterface<T> providerInterface) {
    this.deploymentDetails = deploymentDetails;
    this.providerInterface = providerInterface;
  }

  @Override
  public DeploymentType deploymentType() {
    return DeploymentType.Flotilla;
  }

  @Override
  public Object getService(EndpointType type) {
    return providerInterface.connectTo(deploymentDetails, type);
  }

  @Override
  public SpinnakerEndpoints getEndpoints() {
    return deploymentDetails.getEndpoints();
  }

  private ProviderInterface<T> providerInterface;
  private AccountDeploymentDetails<T> deploymentDetails;

  @Override
  public void deploy() {
    SpinnakerEndpoints.Services services = getEndpoints().getServices();

    providerInterface.deployService(deploymentDetails, services.getRedis());

    providerInterface.deployService(deploymentDetails, services.getClouddriver());
    providerInterface.deployService(deploymentDetails, services.getDeck());
    providerInterface.deployService(deploymentDetails, services.getEcho());
    providerInterface.deployService(deploymentDetails, services.getFiat());
    providerInterface.deployService(deploymentDetails, services.getFront50());
    providerInterface.deployService(deploymentDetails, services.getGate());
    providerInterface.deployService(deploymentDetails, services.getIgor());
    providerInterface.deployService(deploymentDetails, services.getOrca());
    providerInterface.deployService(deploymentDetails, services.getRosco());
  }
}
