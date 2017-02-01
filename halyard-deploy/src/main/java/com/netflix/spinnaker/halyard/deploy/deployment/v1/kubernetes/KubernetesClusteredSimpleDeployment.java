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

package com.netflix.spinnaker.halyard.deploy.deployment.v1.kubernetes;

import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints.Services;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.ClusteredSimpleDeployment;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.provider.v1.KubernetesProviderInterface;

public class KubernetesClusteredSimpleDeployment extends ClusteredSimpleDeployment<KubernetesAccount> {
  public KubernetesClusteredSimpleDeployment(
      DeploymentDetails<KubernetesAccount> deploymentDetails,
      KubernetesProviderInterface providerInterface) {
    super(deploymentDetails, providerInterface);
  }

  @Override
  protected SpinnakerEndpoints specializeEndpoints(SpinnakerEndpoints endpoints) {
    Services services = endpoints.getServices();

    services.getClouddriver().setAddress("spin-clouddriver.spinnaker");
    services.getDeck().setAddress("spin-deck.spinnaker");
    services.getEcho().setAddress("spin-echo.spinnaker");
    services.getFiat().setAddress("spin-fiat.spinnaker");
    services.getFront50().setAddress("spin-front50.spinnaker");
    services.getGate().setAddress("spin-gate.spinnaker");
    services.getIgor().setAddress("spin-igor.spinnaker");
    services.getOrca().setAddress("spin-orca.spinnaker");
    services.getRosco().setAddress("spin-rosco.spinnaker");

    return endpoints;
  }
}
