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
import com.netflix.spinnaker.halyard.config.config.v1.AtomicFileWriter;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.Deployment;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentFactory;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.EndpointFactory;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.GenerateResult;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
  EndpointFactory endpointFactory;

  @Autowired
  ArtifactService artifactService;

  @Autowired
  GenerateService generateService;

  @Autowired
  String spinnakerOutputPath;

  @Autowired
  HalconfigDirectoryStructure halconfigDirectoryStructure;

  public String deploySpinnakerPlan(String deploymentName) {
    // TODO(lwander) https://github.com/spinnaker/halyard/issues/141
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    SpinnakerEndpoints endpoints = endpointFactory.create(deploymentConfiguration);
    BillOfMaterials billOfMaterials = artifactService.getBillOfMaterials(deploymentName);

    StringBuilder result = new StringBuilder();
    result.append("## ENDPOINTS\n\n");
    result.append(generateService.yamlToString(endpoints));
    result.append("\n## VERSIONS\n\n");
    result.append(generateService.yamlToString(billOfMaterials));

    return result.toString();
  }

  public Deployment.DeployResult deploySpinnaker(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    GenerateResult generateResult = generateService.generateConfig(deploymentName);
    Deployment deployment = deploymentFactory.create(deploymentConfiguration, generateResult);

    FileSystem defaultFileSystem = FileSystems.getDefault();
    Path path = defaultFileSystem.getPath(spinnakerOutputPath, "spinnaker.yml");

    log.info("Writing spinnaker endpoints to " + path);

    generateService.atomicWrite(path, generateService.yamlToString(deployment.getEndpoints()));

    Deployment.DeployResult result = deployment.deploy();

    if (!StringUtils.isNullOrEmpty(result.getPostInstallScript())) {
      Path installPath = halconfigDirectoryStructure.getInstallScriptPath(deploymentName);
      AtomicFileWriter writer = null;
      try {
        writer = new AtomicFileWriter(installPath);
        writer.write(result.getPostInstallScript());
        writer.commit();
        result.setScriptPath(installPath.toString());
        installPath.toFile().setExecutable(true);
      } catch (IOException e) {
        throw new HalException(new ConfigProblemBuilder(Problem.Severity.FATAL, "Unable to write post-install script: " + e.getMessage()).build());
      } finally {
        if (writer != null) {
          writer.close();
        }
      }
    }

    return result;
  }
}
