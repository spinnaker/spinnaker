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

import static com.netflix.spinnaker.halyard.config.model.v1.node.Provider.ProviderVersion.V2;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.BakeDeployer;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeployOption;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.Deployer;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DistributedDeployer;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.KubectlDeployer;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.LocalDeployer;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.LocalGitDeployer;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.ServiceProviderFactory;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeployService {
  @Autowired DeploymentService deploymentService;

  @Autowired AccountService accountService;

  @Autowired DistributedDeployer distributedDeployer;

  @Autowired KubectlDeployer kubectlDeployer;

  @Autowired LocalDeployer localDeployer;

  @Autowired LocalGitDeployer localGitDeployer;

  @Autowired BakeDeployer bakeDeployer;

  @Autowired GenerateService generateService;

  @Autowired HalconfigParser halconfigParser;

  @Autowired HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired ServiceProviderFactory serviceProviderFactory;

  @Autowired ArtifactService artifactService;

  @Autowired ConfigParser configParser;

  public void collectLogs(
      String deploymentName, List<String> serviceNames, List<String> excludeServiceNames) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider =
        serviceProviderFactory.create(deploymentConfiguration);
    SpinnakerRuntimeSettings runtimeSettings =
        serviceProvider.buildRuntimeSettings(deploymentConfiguration);
    Deployer deployer = getDeployer(deploymentConfiguration);
    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);

    List<SpinnakerService.Type> serviceTypes =
        serviceNames.stream()
            .map(SpinnakerService.Type::fromCanonicalName)
            .collect(Collectors.toList());

    if (serviceTypes.isEmpty()) {
      serviceTypes =
          serviceProvider.getServices().stream()
              .map(SpinnakerService::getType)
              .collect(Collectors.toList());
    }

    if (!excludeServiceNames.isEmpty()) {
      serviceTypes =
          serviceTypes.stream()
              .filter(serviceType -> !excludeServiceNames.contains(serviceType.getCanonicalName()))
              .collect(Collectors.toList());
    }

    deployer.collectLogs(serviceProvider, deploymentDetails, runtimeSettings, serviceTypes);
  }

  public NodeDiff configDiff(String deploymentName) {
    try {
      DeploymentConfiguration deploymentConfiguration =
          deploymentService.getDeploymentConfiguration(deploymentName);
      halconfigParser.switchToBackupConfig();
      DeploymentConfiguration oldDeploymentConfiguration =
          deploymentService.getDeploymentConfiguration(deploymentName);

      return deploymentConfiguration.diff(oldDeploymentConfiguration);
    } finally {
      halconfigParser.switchToPrimaryConfig();
    }
  }

  public RemoteAction connectCommand(String deploymentName, List<String> serviceNames) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider =
        serviceProviderFactory.create(deploymentConfiguration);
    SpinnakerRuntimeSettings runtimeSettings =
        serviceProvider.buildRuntimeSettings(deploymentConfiguration);
    Deployer deployer = getDeployer(deploymentConfiguration);
    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);

    List<SpinnakerService.Type> serviceTypes =
        serviceNames.stream()
            .map(SpinnakerService.Type::fromCanonicalName)
            .collect(Collectors.toList());

    if (serviceTypes.isEmpty()) {
      serviceTypes.add(SpinnakerService.Type.DECK);
      serviceTypes.add(SpinnakerService.Type.GATE);
    }

    RemoteAction result =
        deployer.connectCommand(serviceProvider, deploymentDetails, runtimeSettings, serviceTypes);
    result.setAutoRun(true);
    result.commitScript(halconfigDirectoryStructure.getConnectScriptPath(deploymentName));
    return result;
  }

  public void clean(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider =
        serviceProviderFactory.create(deploymentConfiguration);

    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);

    RemoteAction action =
        serviceProvider.clean(
            deploymentDetails, serviceProvider.buildRuntimeSettings(deploymentConfiguration));
    action.commitScript(halconfigDirectoryStructure.getUnInstallScriptPath(deploymentName));
  }

  public void rollback(
      String deploymentName, List<String> serviceNames, List<String> excludeServiceNames) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider =
        serviceProviderFactory.create(deploymentConfiguration);

    List<SpinnakerService.Type> serviceTypes =
        serviceNames.stream()
            .map(SpinnakerService.Type::fromCanonicalName)
            .collect(Collectors.toList());

    if (serviceTypes.isEmpty()) {
      serviceTypes =
          serviceProvider.getServices().stream()
              .map(SpinnakerService::getType)
              .collect(Collectors.toList());
    }

    if (!excludeServiceNames.isEmpty()) {
      serviceTypes =
          serviceTypes.stream()
              .filter(serviceType -> !excludeServiceNames.contains(serviceType.getCanonicalName()))
              .collect(Collectors.toList());
    }

    SpinnakerRuntimeSettings runtimeSettings =
        serviceProvider.buildRuntimeSettings(deploymentConfiguration);
    Deployer deployer = getDeployer(deploymentConfiguration);
    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);

    deployer.rollback(serviceProvider, deploymentDetails, runtimeSettings, serviceTypes);
  }

  public RemoteAction prep(
      String deploymentName, List<String> serviceNames, List<String> excludeServiceNames) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);
    Deployer deployer = getDeployer(deploymentConfiguration);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider =
        serviceProviderFactory.create(deploymentConfiguration);
    SpinnakerRuntimeSettings runtimeSettings =
        serviceProvider.buildRuntimeSettings(deploymentConfiguration);

    List<SpinnakerService.Type> serviceTypes =
        serviceNames.stream()
            .map(SpinnakerService.Type::fromCanonicalName)
            .collect(Collectors.toList());

    if (serviceTypes.isEmpty()) {
      serviceTypes =
          serviceProvider.getServices().stream()
              .map(SpinnakerService::getType)
              .collect(Collectors.toList());
    }

    if (!excludeServiceNames.isEmpty()) {
      serviceTypes =
          serviceTypes.stream()
              .filter(serviceType -> !excludeServiceNames.contains(serviceType.getCanonicalName()))
              .collect(Collectors.toList());
    }

    RemoteAction action =
        deployer.prep(serviceProvider, deploymentDetails, runtimeSettings, serviceTypes);

    if (!action.getScript().isEmpty()) {
      action.commitScript(halconfigDirectoryStructure.getPrepScriptPath(deploymentName));
    }

    return action;
  }

  public RemoteAction deploy(
      String deploymentName,
      List<DeployOption> deployOptions,
      List<String> serviceNames,
      List<String> excludeServiceNames,
      Optional<Integer> waitForCompletionTimeoutMinutes) {
    if (deployOptions.contains(DeployOption.DELETE_ORPHANED_SERVICES) && !serviceNames.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot delete orphaned services when services to include are explicitly supplied.");
    }
    if (deployOptions.contains(DeployOption.DELETE_ORPHANED_SERVICES)
        && !excludeServiceNames.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot delete orphaned services when services to exclude are explicitly supplied.");
    }

    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider =
        serviceProviderFactory.create(deploymentConfiguration);

    List<SpinnakerService.Type> serviceTypes =
        serviceNames.stream()
            .map(SpinnakerService.Type::fromCanonicalName)
            .collect(Collectors.toList());

    if (serviceTypes.isEmpty()) {
      serviceTypes =
          serviceProvider.getServices().stream()
              .map(SpinnakerService::getType)
              .collect(Collectors.toList());
    }

    if (!excludeServiceNames.isEmpty()) {
      serviceTypes =
          serviceTypes.stream()
              .filter(serviceType -> !excludeServiceNames.contains(serviceType.getCanonicalName()))
              .collect(Collectors.toList());
    }

    ResolvedConfiguration resolvedConfiguration;
    if (deployOptions.contains(DeployOption.OMIT_CONFIG)) {
      resolvedConfiguration =
          generateService.generateConfig(deploymentName, Collections.emptyList());
    } else {
      resolvedConfiguration = generateService.generateConfig(deploymentName, serviceTypes);
    }

    Path serviceSettingsPath = halconfigDirectoryStructure.getServiceSettingsPath(deploymentName);
    configParser.atomicWrite(serviceSettingsPath, resolvedConfiguration.getRuntimeSettings());

    Path serviceProfilesPath = halconfigDirectoryStructure.getServiceProfilesPath(deploymentName);
    configParser.atomicWrite(serviceProfilesPath, resolvedConfiguration.getServiceProfiles());

    Deployer deployer = getDeployer(deploymentConfiguration);
    DeploymentDetails deploymentDetails = getDeploymentDetails(deploymentConfiguration);

    boolean waitForCompletion = deployOptions.contains(DeployOption.WAIT_FOR_COMPLETION);
    RemoteAction action =
        deployer.deploy(
            serviceProvider,
            deploymentDetails,
            resolvedConfiguration,
            serviceTypes,
            waitForCompletion,
            waitForCompletionTimeoutMinutes);
    halconfigParser.backupConfig();

    if (deployOptions.contains(DeployOption.FLUSH_INFRASTRUCTURE_CACHES)) {
      deployer.flushInfrastructureCaches(
          serviceProvider, deploymentDetails, resolvedConfiguration.getRuntimeSettings());
    }
    if (deployOptions.contains(DeployOption.DELETE_ORPHANED_SERVICES)) {
      deployer.deleteDisabledServices(
          serviceProvider, deploymentDetails, resolvedConfiguration, serviceTypes);
    }

    if (!action.getScript().isEmpty()) {
      action.commitScript(halconfigDirectoryStructure.getInstallScriptPath(deploymentName));
    }

    return action;
  }

  private Deployer getDeployer(DeploymentConfiguration deploymentConfiguration) {
    DeploymentEnvironment deploymentEnvironment =
        deploymentConfiguration.getDeploymentEnvironment();
    DeploymentEnvironment.DeploymentType type = deploymentEnvironment.getType();
    String accountName = deploymentEnvironment.getAccountName();

    switch (type) {
      case BakeDebian:
        return bakeDeployer;
      case LocalGit:
        return localGitDeployer;
      case LocalDebian:
        return localDeployer;
      case Distributed:
        if (StringUtils.isEmpty(accountName)) {
          throw new HalException(
              Problem.Severity.FATAL,
              "An account name must be "
                  + "specified as the desired place to run your distributed deployment.");
        }

        Account account =
            accountService.getAnyProviderAccount(deploymentConfiguration.getName(), accountName);
        Provider.ProviderType providerType = ((Provider) account.getParent()).providerType();

        if (providerType == Provider.ProviderType.KUBERNETES
            && account.getProviderVersion() == V2) {
          return kubectlDeployer;
        } else {
          return distributedDeployer;
        }
      default:
        throw new IllegalArgumentException("Unrecognized deployment type " + type);
    }
  }

  private DeploymentDetails getDeploymentDetails(DeploymentConfiguration deploymentConfiguration) {
    String deploymentName = deploymentConfiguration.getName();
    BillOfMaterials billOfMaterials = artifactService.getBillOfMaterials(deploymentName);
    DeploymentEnvironment.DeploymentType type =
        deploymentConfiguration.getDeploymentEnvironment().getType();
    switch (type) {
      case BakeDebian:
      case LocalDebian:
      case LocalGit:
        return new DeploymentDetails()
            .setDeploymentConfiguration(deploymentConfiguration)
            .setDeploymentName(deploymentName)
            .setBillOfMaterials(billOfMaterials);
      case Distributed:
        DeploymentEnvironment deploymentEnvironment =
            deploymentConfiguration.getDeploymentEnvironment();
        String accountName = deploymentEnvironment.getAccountName();

        if (accountName == null || accountName.isEmpty()) {
          throw new HalException(
              FATAL,
              "An account name must be "
                  + "specified as the desired place to deploy your simple clustered deployment.");
        }
        Account account =
            accountService.getAnyProviderAccount(deploymentConfiguration.getName(), accountName);
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
