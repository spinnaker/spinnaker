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

import com.netflix.spinnaker.halyard.config.model.v1.HalconfigCoordinates;
import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalRequestException;
import com.netflix.spinnaker.halyard.config.model.v1.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.Providers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * deployments.
 */
@Component
public class ProviderService {
  @Autowired
  DeploymentService deploymentService;

  public Provider getProvider(HalconfigCoordinates coordinates) {
    Providers providers = getProviders(coordinates);
    String deploymentName = coordinates.getDeployment();
    String providerName = coordinates.getProvider();

    Provider provider = null;
    if (providerName.toLowerCase().equals("kubernetes")) {
      provider = providers.getKubernetes();
    } else if (providerName.toLowerCase().equals("dockerregistry")) {
      provider = providers.getDockerRegistry();
    } else if (providerName.toLowerCase().equals("google")) {
      provider = providers.getGoogle();
    } else {
      throw new IllegalRequestException(coordinates,
          "There is no support for managing the selected provider using halyard",
          "You either made a typo, or should file a feature request: https://github.com/spinnaker/spinnaker/issues");
    }

    if (provider == null) {
      throw new IllegalRequestException(coordinates,
          "The selected provider was not configured",
          "Add an account for the selected provider, or pick a different provider");
    }

    return provider;
  }

  public Providers getProviders(HalconfigCoordinates coordinates) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(coordinates);

    Providers providers = deploymentConfiguration.getProviders();
    if (providers == null) {
      throw new IllegalRequestException(coordinates,
          "The selected deployment has no providers configured",
          "Add an account for any provider, or pick a different deployment");
    }

    return providers;
  }
}
