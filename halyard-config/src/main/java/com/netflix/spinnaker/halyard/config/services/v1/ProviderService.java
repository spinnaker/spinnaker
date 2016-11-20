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

import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalRequestException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity.FATAL;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * deployments.
 */
@Component
public class ProviderService {
  @Autowired
  DeploymentService deploymentService;

  public Provider getProvider(NodeReference reference) {
    Providers providers = getProviders(reference);
    String deploymentName = reference.getDeployment();
    String providerName = reference.getProvider();

    Provider provider = null;
    if (providerName.toLowerCase().equals("kubernetes")) {
      provider = providers.getKubernetes();
    } else if (providerName.toLowerCase().equals("dockerregistry")) {
      provider = providers.getDockerRegistry();
    } else if (providerName.toLowerCase().equals("google")) {
      provider = providers.getGoogle();
    } else {
      throw new IllegalRequestException(new ProblemBuilder(
          FATAL, "There is no support for managing the selected provider using halyard")
          .setReference(reference)
          .setRemediation("You either made a typo, or should file a feature request: https://github.com/spinnaker/spinnaker/issues").build());
    }

    if (provider == null) {
      throw new IllegalRequestException(new ProblemBuilder(
          FATAL, "The selected provider was not configured")
          .setReference(reference)
          .setRemediation("Add an account for the selected provider, or pick a different provider").build());
    }

    return provider;
  }

  public Providers getProviders(NodeReference reference) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(reference);

    Providers providers = deploymentConfiguration.getProviders();
    if (providers == null) {
      throw new IllegalRequestException(new ProblemBuilder(
          FATAL, "The selected deployment has no providers configured")
          .setReference(reference)
          .setRemediation("Add an account for any provider, or pick a different deployment").build());
    }

    return providers;
  }
}
