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
import com.netflix.spinnaker.halyard.config.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.component.v1.ComponentType;
import com.netflix.spinnaker.halyard.deploy.provider.v1.ProviderInterface;

abstract public class ClusteredSimpleDeployment<T extends Account> extends Deployment {
  public ClusteredSimpleDeployment(DeploymentDetails<T> deploymentDetails, ProviderInterface<T> providerInterface) {
    deploymentDetails.setEndpoints(specializeEndpoints(deploymentDetails.getEndpoints()));

    this.deploymentDetails = deploymentDetails;
    this.providerInterface = providerInterface;
  }

  protected abstract SpinnakerEndpoints specializeEndpoints(SpinnakerEndpoints endpoints);

  @Override
  public DeploymentType deploymentType() {
    return DeploymentType.ClusteredSimple;
  }

  @Override
  public Object getService(ComponentType type) {
    return providerInterface.connectTo(deploymentDetails, type);
  }

  @Override
  public SpinnakerEndpoints getEndpoints() {
    return deploymentDetails.getEndpoints();
  }

  private ProviderInterface<T> providerInterface;
  private DeploymentDetails<T> deploymentDetails;

  @Override
  public void deploy() {
    providerInterface.bootstrapClouddriver(deploymentDetails);
    providerInterface.connectTo(deploymentDetails, ComponentType.CLOUDDRIVER);
  }
}
