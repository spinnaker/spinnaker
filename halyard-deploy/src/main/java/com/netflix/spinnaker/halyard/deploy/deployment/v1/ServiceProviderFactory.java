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
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeployableServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.InstallableServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.kubernetes.KubernetesServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.debian.DebianServiceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType.LocalDebian;

@Component
public class ServiceProviderFactory {
  @Autowired
  AccountService accountService;

  @Autowired
  KubernetesServiceProvider kubernetesServiceProvider;

  @Autowired
  DebianServiceProvider debianServiceProvider;

  public SpinnakerServiceProvider create(DeploymentConfiguration deploymentConfiguration) {
    DeploymentEnvironment.DeploymentType type = deploymentConfiguration.getDeploymentEnvironment().getType();
    // TODO(lwander) what's the best UX here? mashing together deploys & installs feels wrong.
    switch (type) {
      case LocalDebian:
        return debianServiceProvider;
      case Distributed:
        return createDeployableServiceProvider(deploymentConfiguration);
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }

  public InstallableServiceProvider createInstallableServiceProvider(DeploymentConfiguration deploymentConfiguration) {
    DeploymentEnvironment.DeploymentType type = deploymentConfiguration.getDeploymentEnvironment().getType();
    switch (type) {
      case LocalDebian:
        return debianServiceProvider;
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }

  public DeployableServiceProvider createDeployableServiceProvider(DeploymentConfiguration deploymentConfiguration) {
    DeploymentEnvironment deploymentEnvironment = deploymentConfiguration.getDeploymentEnvironment();
    String accountName = deploymentEnvironment.getAccountName();

    if (accountName == null || accountName.isEmpty()) {
      throw new HalException(new ConfigProblemBuilder(Problem.Severity.FATAL, "An account name must be "
          + "specified as the desired place to run your simple clustered deployment.").build());
    }

    Account account = accountService.getAnyProviderAccount(deploymentConfiguration.getName(), accountName);
    Provider.ProviderType providerType = ((Provider) account.getParent()).providerType();

    switch (providerType) {
      case KUBERNETES:
        return kubernetesServiceProvider;
      default:
        throw new IllegalArgumentException("No Clustered Simple Deployment for " + providerType.getId());
    }
  }
}
