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
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.EndpointFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ProfileConfig;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.SpinnakerProfile;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Component
@Slf4j
public class GenerateService {
  @Autowired
  private String spinnakerOutputPath;

  @Autowired
  private HalconfigParser halconfigParser;

  @Autowired
  private DeploymentService deploymentService;

  @Autowired
  private EndpointFactory endpointFactory;

  @Autowired
  private String halconfigPath;

  @Autowired
  private HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired(required = false)
  private List<SpinnakerProfile> spinnakerProfiles = new ArrayList<>();

  @Autowired
  private ConfigParser configParser;

  /**
   * Generate config for a given deployment.
   *
   * This involves a few steps:
   *
   *   1. Clear out old config generated in a prior run.
   *   2. Generate configuration using the halconfig as the source of truth, while collecting files needed by
   *      the deployment.
   *   3. Copy custom profiles from the specified deployment over to the new deployment.
   *
   * @param deploymentName is the deployment whose config to generate
   * @return a mapping from components to the profile's required local files.
   */
  public GenerateResult generateConfig(String deploymentName) {
    log.info("Generating config from \"" + halconfigPath + "\" with deploymentName \"" + deploymentName + "\"");
    File spinnakerOutput = new File(spinnakerOutputPath);
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);

    SpinnakerEndpoints endpoints = endpointFactory.create(deploymentConfiguration);

    // Step 1.
    try {
      FileUtils.deleteDirectory(spinnakerOutput);
    } catch (IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL, "Unable to clear old spinnaker config: " + e.getMessage() + ".").build());
    }

    if (!spinnakerOutput.mkdirs()) {
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL, "Unable to create new spinnaker config directory \"" + spinnakerOutputPath + "\".").build());
    }

    // Step 2.
    DaemonTaskHandler.newStage("Generating all Spinnaker profile files");
    Map<String, List<String>> profileRequirements = new HashMap<>();
    Map<SpinnakerArtifact, String> artifactVersions = new HashMap<>();
    FileSystem defaultFileSystem = FileSystems.getDefault();
    Path path;
    for (SpinnakerProfile profile : spinnakerProfiles) {
      String artifactName = profile.getArtifact().getName();

      ProfileConfig config = profile.getFullConfig(deploymentName, endpoints);
      for (Map.Entry<String, String> e : config.getConfigContents().entrySet()) {
        String outputFileName = e.getKey();
        path = defaultFileSystem.getPath(spinnakerOutputPath, outputFileName);
        log.info("Writing " + artifactName + " profile to " + path + " with " + config.getRequiredFiles().size() + " required files");
        DaemonTaskHandler.log("Writing profile " + outputFileName);

        configParser.atomicWrite(path, e.getValue());
      }

      profileRequirements.put(artifactName, config.getRequiredFiles());
      artifactVersions.put(profile.getArtifact(), config.getVersion());
    }

    // Step 3.
    Path userProfilePath = halconfigDirectoryStructure.getUserProfilePath(deploymentName);

    if (Files.isDirectory(userProfilePath)) {
      DaemonTaskHandler.newStage("Copying user-provided profiles");
      File[] files = userProfilePath.toFile().listFiles();
      if (files == null) {
        files = new File[0];
      }

      Arrays.stream(files).forEach(f -> {
        try {
          DaemonTaskHandler.log("Copying existing profile " + f.getName());
          Files.copy(f.toPath(), Paths.get(spinnakerOutput.toString(), f.getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          throw new HalException(
              new ConfigProblemBuilder(Severity.FATAL, "Unable to copy profile \"" + f.getName() + "\": " + e.getMessage() + ".").build()
          );
        }
      });
    }

    // Step 4.
    GenerateResult result = new GenerateResult()
        .setArtifactVersions(artifactVersions)
        .setProfileRequirements(profileRequirements)
        .setEndpoints(endpoints);

    return result;
  }

  @Data
  public static class GenerateResult {
    private Map<String, List<String>> profileRequirements = new HashMap<>();
    private Map<SpinnakerArtifact, String> artifactVersions = new HashMap<>();
    SpinnakerEndpoints endpoints;

    public String getArtifactVersion(SpinnakerArtifact artifact) {
      return artifactVersions.get(artifact);
    }
  }
}
