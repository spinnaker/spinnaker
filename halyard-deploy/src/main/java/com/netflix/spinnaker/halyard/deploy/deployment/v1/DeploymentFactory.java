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

import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.kubernetes.KubernetesClusteredSimpleDeployment;
import com.netflix.spinnaker.halyard.deploy.provider.v1.KubernetesProviderInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.halyard.config.model.v1.node.Provider.*;

@Component
public class DeploymentFactory {
  @Autowired
  AccountService accountService;

  @Autowired
  KubernetesProviderInterface kubernetesProviderInterface;

  public Deployment create(DeploymentConfiguration deploymentConfiguration) {
    DeploymentType type = deploymentConfiguration.getDeploymentEnvironment().getType();
    switch (type) {
      case LocalhostDebian:
        return new LocalhostDebianDeployment();
      case ClusteredSimple:
        return createClusteredSimpleDeployment(deploymentConfiguration);
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }

  private Deployment createClusteredSimpleDeployment(DeploymentConfiguration deploymentConfiguration) {
    DeploymentEnvironment deploymentEnvironment = deploymentConfiguration.getDeploymentEnvironment();
    String accountName = deploymentEnvironment.getAccountName();

    if (accountName == null || accountName.isEmpty()) {
      throw new HalconfigException(new ProblemBuilder(Severity.FATAL, "An account name must be "
          + "specified as the desired place to run your simple clustered deployment.").build());
    }

    NodeFilter nodeFilter = new NodeFilter()
        .setDeployment(deploymentConfiguration.getName())
        .setProvider("*")
        .setAccount(accountName);

    Account account = accountService.getAccount(nodeFilter);
    ProviderType providerType = ((Provider) account.getParent()).providerType();

    switch (providerType) {
      case KUBERNETES:
        DeploymentDetails deploymentDetails = new DeploymentDetails<>()
            .setAccount(account)
            .setDeploymentEnvironment(deploymentEnvironment)
            .setEndpoints(new SpinnakerEndpoints())
            .setDeploymentName(deploymentConfiguration.getName());

        return new KubernetesClusteredSimpleDeployment(deploymentDetails, kubernetesProviderInterface);
      default:
        throw new IllegalArgumentException("No Clustered Simple Deployment for " + providerType.getId());
    }
  }
}
