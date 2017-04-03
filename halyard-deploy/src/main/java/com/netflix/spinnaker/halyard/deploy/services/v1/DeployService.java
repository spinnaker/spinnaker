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

package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.*;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeployableServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.InstallableServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

@Component
@Slf4j
public class DeployService {
  @Autowired
  DeploymentService deploymentService;

  @Autowired
  AccountService accountService;

  @Autowired
  DistributedDeployer distributedDeployer;

  @Autowired
  LocalInstaller localInstaller;

  @Autowired
  GenerateService generateService;

  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired
  ServiceProviderFactory serviceProviderFactory;

  @Autowired
  ArtifactService artifactService;

  @Autowired
  ConfigParser configParser;

  public NodeDiff configDiff(String deploymentName) {
    try {
      DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
      halconfigParser.switchToBackupConfig(deploymentName);
      DeploymentConfiguration oldDeploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);

      return deploymentConfiguration.diff(oldDeploymentConfiguration);
    } finally {
      halconfigParser.switchToPrimaryConfig();
    }
  }

  public void rollback(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    DeploymentEnvironment.DeploymentType type = deploymentConfiguration.getDeploymentEnvironment().getType();
    switch (type) {
      case LocalDebian:
        throw new HalException(FATAL, "Rollback for debians has not been implemented yet.");
      case Distributed:
        rollbackDistributed(deploymentName);
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }

  public RemoteAction deploy(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    DeploymentEnvironment.DeploymentType type = deploymentConfiguration.getDeploymentEnvironment().getType();
    switch (type) {
      case LocalDebian:
        return installSpinnaker(deploymentName);
      case Distributed:
        return deploySpinnaker(deploymentName);
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }

  private RemoteAction installSpinnaker(String deploymentName) {
    halconfigParser.backupConfig(deploymentName);

    ResolvedConfiguration resolvedConfiguration = generateService.generateConfig(deploymentName);
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    InstallableServiceProvider serviceProvider = serviceProviderFactory.createInstallableServiceProvider(deploymentConfiguration);
    DeploymentDetails deploymentDetails = new DeploymentDetails()
        .setDeploymentConfiguration(deploymentConfiguration)
        .setDeploymentName(deploymentName)
        .setBillOfMaterials(artifactService.getBillOfMaterials(deploymentName));

    RemoteAction result = localInstaller.install(deploymentDetails, resolvedConfiguration, serviceProvider);
    result.commitScript(halconfigDirectoryStructure.getInstallScriptPath(deploymentName));
    return result;
  }

  private void rollbackDistributed(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    DeploymentEnvironment deploymentEnvironment = deploymentConfiguration.getDeploymentEnvironment();
    String accountName = deploymentEnvironment.getAccountName();

    if (accountName == null || accountName.isEmpty()) {
      throw new HalException(FATAL, "An account name must be "
          + "specified as the desired place to run your simple clustered deployment.");
    }

    Account account = accountService.getAnyProviderAccount(deploymentConfiguration.getName(), accountName);

    DeployableServiceProvider serviceProvider = serviceProviderFactory.createDeployableServiceProvider(deploymentConfiguration);
    SpinnakerRuntimeSettings runtimeSettings = serviceProvider.buildEndpoints(deploymentConfiguration);
    AccountDeploymentDetails deploymentDetails = (AccountDeploymentDetails) new AccountDeploymentDetails()
        .setAccount(account)
        .setDeploymentConfiguration(deploymentConfiguration)
        .setDeploymentName(deploymentName)
        .setBillOfMaterials(artifactService.getBillOfMaterials(deploymentName));

    distributedDeployer.rollback(serviceProvider, deploymentDetails, runtimeSettings);
  }

  private RemoteAction deploySpinnaker(String deploymentName) {
    halconfigParser.backupConfig(deploymentName);

    ResolvedConfiguration resolvedConfiguration = generateService.generateConfig(deploymentName);
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    DeploymentEnvironment deploymentEnvironment = deploymentConfiguration.getDeploymentEnvironment();
    String accountName = deploymentEnvironment.getAccountName();

    if (accountName == null || accountName.isEmpty()) {
      throw new HalException(FATAL, "An account name must be "
          + "specified as the desired place to run your simple clustered deployment.");
    }

    Account account = accountService.getAnyProviderAccount(deploymentConfiguration.getName(), accountName);

    DeployableServiceProvider serviceProvider = serviceProviderFactory.createDeployableServiceProvider(deploymentConfiguration);
    AccountDeploymentDetails deploymentDetails = (AccountDeploymentDetails) new AccountDeploymentDetails()
        .setAccount(account)
        .setDeploymentConfiguration(deploymentConfiguration)
        .setDeploymentName(deploymentName)
        .setBillOfMaterials(artifactService.getBillOfMaterials(deploymentName));

    RemoteAction result = distributedDeployer.deploy(serviceProvider, deploymentDetails, resolvedConfiguration);
    result.commitScript(halconfigDirectoryStructure.getInstallScriptPath(deploymentName));
    return result;
  }
}
