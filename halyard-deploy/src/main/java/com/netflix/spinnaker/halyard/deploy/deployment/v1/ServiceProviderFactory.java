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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.debian.BakeDebianServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.google.GoogleDistributedServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v1.KubernetesV1DistributedServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubectlServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.debian.LocalDebianServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git.LocalGitServiceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ServiceProviderFactory {
  @Autowired
  AccountService accountService;

  @Autowired
  KubernetesV1DistributedServiceProvider kubernetesV1DistributedServiceProvider;

  @Autowired
  KubectlServiceProvider kubectlServiceProvider;

  @Autowired
  GoogleDistributedServiceProvider googleDistributedServiceProvider;

  @Autowired
  LocalDebianServiceProvider localDebianServiceProvider;

  @Autowired
  LocalGitServiceProvider localGitServiceProvider;

  @Autowired
  BakeDebianServiceProvider bakeDebianServiceProvider;

  public SpinnakerServiceProvider create(DeploymentConfiguration deploymentConfiguration) {
    DeploymentEnvironment.DeploymentType type = deploymentConfiguration.getDeploymentEnvironment().getType();
    // TODO(lwander) what's the best UX here? mashing together deploys & installs feels wrong.
    switch (type) {
      case BakeDebian:
        return bakeDebianServiceProvider;
      case LocalDebian:
        return localDebianServiceProvider;
      case LocalGit:
        return localGitServiceProvider;
      case Distributed:
        return createDeployableServiceProvider(deploymentConfiguration);
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }

  private SpinnakerServiceProvider createDeployableServiceProvider(DeploymentConfiguration deploymentConfiguration) {
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
        switch (account.getProviderVersion()) {
          case V1:
            return kubernetesV1DistributedServiceProvider;
          case V2:
            return kubectlServiceProvider;
          default:
            return kubernetesV1DistributedServiceProvider;
        }
      case GOOGLE:
        return googleDistributedServiceProvider;
      default:
        throw new IllegalArgumentException("No Clustered Simple Deployment for " + providerType.getName());
    }
  }
}
