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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.AtomicFileWriter;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
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
import org.yaml.snakeyaml.Yaml;

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
  private String halconfigDirectory;

  @Autowired
  private DeploymentService deploymentService;

  @Autowired
  private EndpointFactory endpointFactory;

  @Autowired
  private String halconfigPath;

  @Autowired
  private Yaml yamlParser;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired(required = false)
  private List<SpinnakerProfile> spinnakerProfiles = new ArrayList<>();

  void atomicWrite(Path path, String contents) {
    AtomicFileWriter writer = null;
    try {
      writer = new AtomicFileWriter(path);
      writer.write(contents);
      writer.commit();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL,
              "Failed to write config for profile " + path.toFile().getName() + ": " + ioe
                  .getMessage()).build()
      );
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  String yamlToString(Object yaml) {
    return yamlParser.dump(objectMapper.convertValue(yaml, Map.class));
  }

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
    Map<String, List<String>> profileRequirements = new HashMap<>();
    Map<SpinnakerArtifact, String> artifactVersions = new HashMap<>();
    FileSystem defaultFileSystem = FileSystems.getDefault();
    Path path;
    for (SpinnakerProfile profile : spinnakerProfiles) {
      path = defaultFileSystem.getPath(spinnakerOutputPath, profile.getProfileFileName());
      ProfileConfig config = profile.getFullConfig(deploymentName, endpoints);
      log.info("Writing " + profile.getProfileName() + " profile to " + path + " with " + config.getRequiredFiles().size() + " required files");
      DaemonTaskHandler.log("Writing profile " + path.getFileName().toFile().getName());
      atomicWrite(path, config.getConfigContents());

      profileRequirements.put(profile.getProfileName(), config.getRequiredFiles());
      artifactVersions.put(profile.getArtifact(), config.getVersion());
    }

    // Step 3.
    Path userProfilePath = Paths.get(halconfigDirectory, deploymentName);

    if (Files.isDirectory(userProfilePath)) {
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

    return new GenerateResult()
        .setArtifactVersion(artifactVersions)
        .setProfileRequirements(profileRequirements)
        .setEndpoints(endpoints);
  }

  @Data
  public static class GenerateResult {
    private Map<String, List<String>> profileRequirements = new HashMap<>();
    private Map<SpinnakerArtifact, String> artifactVersion = new HashMap<>();
    SpinnakerEndpoints endpoints;
  }
}
