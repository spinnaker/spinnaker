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
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.*;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
  LocalDeployer localDeployer;

  @Autowired
  BakeDeployer bakeDeployer;

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

  public void clean(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider = serviceProviderFactory.create(deploymentConfiguration);

    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);

    RemoteAction action = serviceProvider.clean(deploymentDetails, serviceProvider.buildRuntimeSettings(deploymentConfiguration));
    action.commitScript(halconfigDirectoryStructure.getUnInstallScriptPath(deploymentName));
  }

  public void rollback(String deploymentName, List<String> serviceNames) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider = serviceProviderFactory.create(deploymentConfiguration);

    if (serviceNames.isEmpty()) {
      serviceNames = serviceProvider
          .getServices()
          .stream()
          .map(SpinnakerService::getCanonicalName)
          .collect(Collectors.toList());
    }

    SpinnakerRuntimeSettings runtimeSettings = serviceProvider.buildRuntimeSettings(deploymentConfiguration);
    Deployer deployer = getDeployer(deploymentConfiguration);
    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);

    deployer.rollback(serviceProvider, deploymentDetails, runtimeSettings, serviceNames);
  }

  public RemoteAction deploy(String deploymentName, List<DeployOption> deployOptions, List<String> serviceNames) {
    halconfigParser.backupConfig(deploymentName);

    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider = serviceProviderFactory.create(deploymentConfiguration);

    if (serviceNames.isEmpty()) {
      serviceNames = serviceProvider
          .getServices()
          .stream()
          .map(SpinnakerService::getCanonicalName)
          .collect(Collectors.toList());
    }

    ResolvedConfiguration resolvedConfiguration;
    if (deployOptions.contains(DeployOption.OMIT_CONFIG)) {
      resolvedConfiguration = generateService.generateConfig(deploymentName, Collections.emptyList());
    } else {
      resolvedConfiguration = generateService.generateConfig(deploymentName, serviceNames);
    }

    Path path = halconfigDirectoryStructure.getGenerateResultPath(deploymentName);
    configParser.atomicWrite(path, resolvedConfiguration);

    Deployer deployer = getDeployer(deploymentConfiguration);
    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);

    RemoteAction action = deployer.deploy(serviceProvider, deploymentDetails, resolvedConfiguration, serviceNames);
    action.commitScript(halconfigDirectoryStructure.getInstallScriptPath(deploymentName));
    return action;
  }

  private Deployer getDeployer(DeploymentConfiguration deploymentConfiguration) {
    DeploymentEnvironment.DeploymentType type = deploymentConfiguration.getDeploymentEnvironment().getType();
    switch (type) {
      case BakeDebian:
        return bakeDeployer;
      case LocalDebian:
        return localDeployer;
      case Distributed:
        return distributedDeployer;
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }

  private DeploymentDetails getDeploymentDetails(DeploymentConfiguration deploymentConfiguration) {
    String deploymentName = deploymentConfiguration.getName();
    BillOfMaterials billOfMaterials = artifactService.getBillOfMaterials(deploymentName);
    DeploymentEnvironment.DeploymentType type = deploymentConfiguration.getDeploymentEnvironment().getType();
    switch (type) {
      case BakeDebian:
      case LocalDebian:
        return new DeploymentDetails()
            .setDeploymentConfiguration(deploymentConfiguration)
            .setDeploymentName(deploymentName)
            .setBillOfMaterials(billOfMaterials);
      case Distributed:
        DeploymentEnvironment deploymentEnvironment = deploymentConfiguration.getDeploymentEnvironment();
        String accountName = deploymentEnvironment.getAccountName();

        if (accountName == null || accountName.isEmpty()) {
          throw new HalException(FATAL, "An account name must be "
              + "specified as the desired place to deploy your simple clustered deployment.");
        }
        Account account = accountService.getAnyProviderAccount(deploymentConfiguration.getName(), accountName);
        return new AccountDeploymentDetails()
            .setAccount(account)
            .setDeploymentConfiguration(deploymentConfiguration)
            .setDeploymentName(deploymentName)
            .setBillOfMaterials(billOfMaterials);
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }
}
