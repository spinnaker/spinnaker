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

import com.amazonaws.util.StringUtils;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.Deployment;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentFactory;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.GenerateResult;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

@Component
@Slf4j
public class DeployService {
  @Autowired
  DeploymentService deploymentService;

  @Autowired
  DeploymentFactory deploymentFactory;

  @Autowired
  GenerateService generateService;

  @Autowired
  String spinnakerOutputPath;

  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  HalconfigDirectoryStructure halconfigDirectoryStructure;

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

  public RemoteAction installSpinnaker(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);

    Deployment deployment = deploymentFactory.create(deploymentConfiguration, null);

    RemoteAction result = deployment.install(spinnakerOutputPath);

    String script = result.getScript();
    if (!StringUtils.isNullOrEmpty(script)) {
      String resultPath = writeExecutable(script, halconfigDirectoryStructure.getInstallScriptPath(deploymentName));
      result.setScriptPath(resultPath);
      result.setAutoRun(true);
    }

    return result;
  }

  public RemoteAction deploySpinnaker(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    GenerateResult generateResult = generateService.generateConfig(deploymentName);
    Path generateResultPath = halconfigDirectoryStructure.getGenerateResultPath(deploymentName);
    configParser.atomicWrite(generateResultPath, configParser.yamlToString(generateResult));
    halconfigParser.backupConfig(deploymentName);

    Deployment deployment = deploymentFactory.create(deploymentConfiguration, generateResult);

    FileSystem defaultFileSystem = FileSystems.getDefault();
    Path path = defaultFileSystem.getPath(spinnakerOutputPath, "spinnaker.yml");

    log.info("Writing spinnaker endpoints to " + path);

    configParser.atomicWrite(path, configParser.yamlToString(deployment.getEndpoints()));

    RemoteAction result = deployment.deploy(spinnakerOutputPath);

    String script = result.getScript();
    if (!StringUtils.isNullOrEmpty(script)) {
      String resultPath = writeExecutable(script, halconfigDirectoryStructure.getInstallScriptPath(deploymentName));
      result.setScriptPath(resultPath);
      result.setAutoRun(true);
    }

    return result;
  }

  private String writeExecutable(String contents, Path path) {
    configParser.atomicWrite(path, contents);
    path.toFile().setExecutable(true);
    return path.toString();
  }

  public RunningServiceDetails getRunningServiceDetails(String deploymentName, String serviceName) {
    try {
      halconfigParser.switchToBackupConfig(deploymentName);

      DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
      Deployment deployment = deploymentFactory.create(deploymentConfiguration, getPriorGenerateResult(deploymentName));
      SpinnakerEndpoints endpoints = deployment.getEndpoints();

      return deployment.getServiceDetails(endpoints.getService(serviceName));
    } finally {
      halconfigParser.switchToPrimaryConfig();
    }
  }

  private GenerateResult getPriorGenerateResult(String deploymentName) {
    Path generateResultPath = halconfigDirectoryStructure.getGenerateResultPath(deploymentName);
    if (!generateResultPath.toFile().exists()) {
      throw new HalException(
          new ProblemBuilder(Problem.Severity.FATAL, "Spinnaker has not yet been deployed, so there are no services to observe.").build()
      );
    }

    return configParser.read(generateResultPath, GenerateResult.class);
  }

  private boolean deploymentExists(String deploymentName) {
    return halconfigDirectoryStructure.getBackupConfigPath(deploymentName).toFile().exists();
  }
}
