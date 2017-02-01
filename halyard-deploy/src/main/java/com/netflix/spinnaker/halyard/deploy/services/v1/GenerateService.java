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
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ProfileConfig;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.SpinnakerProfile;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.Deployment;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;

@Component
@Slf4j
public class GenerateService {
  @Autowired
  String spinnakerOutputPath;

  @Autowired
  String halconfigDirectory;

  @Autowired
  DeploymentService deploymentService;

  @Autowired
  DeploymentFactory deploymentFactory;

  @Autowired
  String halconfigPath;

  @Autowired
  Yaml yamlParser;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired(required = false)
  List<SpinnakerProfile> spinnakerProfiles = new ArrayList<>();

  void atomicWrite(Path path, String contents) {
    AtomicFileWriter writer = null;
    try {
      writer = new AtomicFileWriter(path);
      writer.write(contents);
      writer.commit();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new HalconfigException(
          new ProblemBuilder(Severity.FATAL,
              "Failed to write config for profile " + path.toFile().getName() + ": " + ioe
                  .getMessage()).build()
      );
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  private String yamlToString(Object yaml) {
    return yamlParser.dump(objectMapper.convertValue(yaml, Map.class));
  }

  /**
   * Generate config for a given deployment.
   *
   * This involves a few steps:
   *
   *   1. Clear out old config generated in a prior run.
   *   2. Determine what the deployment footprint looks like to provide endpoint information for each service.
   *   3. Generate configuration using the halconfig as the source of truth, while collecting files needed by
   *      the deployment.
   *   4. Copy custom profiles from the specified deployment over to the new deployment.
   *
   * @param nodeFilter A filter that specifies the deployment to use.
   * @return a mapping from components to the profile's required local files.
   */
  public Map<String, List<String>> generateConfig(NodeFilter nodeFilter) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(nodeFilter);
    String deploymentName = deploymentConfiguration.getName();

    log.info("Generating config from \"" + halconfigPath + "\" with deploymentName \"" + deploymentName + "\"");

    File spinnakerOutput = new File(spinnakerOutputPath);

    // Step 1.
    try {
      FileUtils.deleteDirectory(spinnakerOutput);
    } catch (IOException e) {
      throw new HalconfigException(
          new ProblemBuilder(Severity.FATAL, "Unable to clear old spinnaker config: " + e.getMessage() + ".").build());
    }

    if (!spinnakerOutput.mkdirs()) {
      throw new HalconfigException(
          new ProblemBuilder(Severity.FATAL, "Unable to create new spinnaker config directory \"" + spinnakerOutputPath + "\".").build());
    }

    // Step 2.
    AtomicFileWriter writer = null;
    Deployment deployment = deploymentFactory.create(deploymentConfiguration);
    FileSystem defaultFileSystem = FileSystems.getDefault();
    Path path = defaultFileSystem.getPath(spinnakerOutputPath, "spinnaker.yml");

    SpinnakerEndpoints endpoints = deployment.getEndpoints();

    log.info("Writing spinnaker endpoints");
    atomicWrite(path, yamlToString(deployment.getEndpoints()));

    Map<String, List<String>> requiredFiles = new HashMap<>();

    // Step 3.
    for (SpinnakerProfile profile : spinnakerProfiles) {
      path = defaultFileSystem.getPath(spinnakerOutputPath, profile.getProfileFileName());
      ProfileConfig config = profile.getFullConfig(nodeFilter, endpoints);
      log.info("Writing " + profile.getProfileName() + " profile");
      atomicWrite(path, config.getConfigContents());

      requiredFiles.put(profile.getProfileName(), config.getRequiredFiles());
    }

    // Step 4.
    Path userProfilePath = Paths.get(halconfigDirectory, deploymentName);

    if (Files.isDirectory(userProfilePath)) {
      File[] files = userProfilePath.toFile().listFiles();
      if (files == null) {
        files = new File[0];
      }

      Arrays.stream(files).forEach(f -> {
        try {
          Files.copy(f.toPath(), Paths.get(spinnakerOutput.toString(), f.getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          throw new HalconfigException(
              new ProblemBuilder(Severity.FATAL, "Unable to copy profile \"" + f.getName() + "\": " + e.getMessage() + ".").build()
          );
        }
      });
    }

    return requiredFiles;
  }
}
