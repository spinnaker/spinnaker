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
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.ServiceProviderFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GenerateService {
  @Autowired
  private String spinnakerStagingPath;

  @Autowired
  private DeploymentService deploymentService;

  @Autowired
  private ServiceProviderFactory serviceProviderFactory;

  @Autowired
  private String halconfigPath;

  @Autowired
  private HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired
  private List<SpinnakerService> spinnakerServices = new ArrayList<>();

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
   *
   * @param deploymentName is the deployment whose config to generate
   * @param services is the list of services to generate configs for
   * @return a mapping from components to the profile's required local files.
   */
  public ResolvedConfiguration generateConfig(String deploymentName, List<String> services) {
    DaemonTaskHandler.newStage("Generating all Spinnaker profile files and endpoints");
    log.info("Generating config from \"" + halconfigPath + "\" with deploymentName \"" + deploymentName + "\"");
    File spinnakerStaging = new File(spinnakerStagingPath);
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);

    DaemonTaskHandler.message("Building service endpoints");
    SpinnakerServiceProvider<DeploymentDetails> serviceProvider = serviceProviderFactory.create(deploymentConfiguration);
    SpinnakerRuntimeSettings runtimeSettings = serviceProvider.buildRuntimeSettings(deploymentConfiguration);

    // Step 1.
    try {
      FileUtils.deleteDirectory(spinnakerStaging);
    } catch (IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL, "Unable to clear old spinnaker config: " + e.getMessage() + ".").build());
    }

    Path userProfilePath = halconfigDirectoryStructure.getUserProfilePath(deploymentName);
    List<String> userProfileNames = aggregateProfilesInPath(userProfilePath.toString(), "");

    // Step 2.
    Map<SpinnakerService.Type, Map<String, Profile>> serviceProfiles = new HashMap<>();
    for (SpinnakerService service : serviceProvider.getServices()) {
      boolean isDesiredService = services
          .stream()
          .filter(s -> s.equalsIgnoreCase(service.getCanonicalName()))
          .count() > 0;

      if (!isDesiredService) {
        continue;
      }

      ServiceSettings settings = runtimeSettings.getServiceSettings(service);
      if (settings == null || !settings.getEnabled()) {
        continue;
      }

      List<Profile> profiles = service.getProfiles(deploymentConfiguration, runtimeSettings);

      String pluralModifier = profiles.size() == 1 ? "" : "s";
      String profileMessage = "Generated " + profiles.size() + " profile" + pluralModifier;
      Map<String, Profile> outputProfiles = processProfiles(profiles);

      List<Profile> customProfiles = userProfileNames.stream()
          .map(s -> (Optional<Profile>) service.customProfile(deploymentConfiguration, runtimeSettings, Paths.get(userProfilePath.toString(), s), s))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());

      pluralModifier = customProfiles.size() == 1 ? "" : "s";
      profileMessage += " and discovered " + customProfiles.size() + " custom profile" + pluralModifier + " for " + service.getCanonicalName();
      DaemonTaskHandler.message(profileMessage);
      outputProfiles.putAll(processProfiles(customProfiles));

      serviceProfiles.put(service.getType(), outputProfiles);
    }

    return new ResolvedConfiguration()
        .setServiceProfiles(serviceProfiles)
        .setRuntimeSettings(runtimeSettings);
  }

  private Map<String, Profile> processProfiles(List<Profile> profiles) {
    for (Profile profile : profiles) {
      profile.writeStagedFile(spinnakerStagingPath);
    }

    Map<String, Profile> profileMap = new HashMap<>();
    for (Profile profile : profiles) {
      profileMap.put(profile.getName(), profile);
    }

    return profileMap;
  }


  private static List<String> aggregateProfilesInPath(String basePath, String relativePath) {
    String filePrefix;
    if (!relativePath.isEmpty()) {
      filePrefix = relativePath + File.separator;
    } else {
      filePrefix = relativePath;
    }

    File currentPath = new File(basePath, relativePath);
    return Arrays.stream(currentPath.listFiles())
        .map(f -> f.isFile() ? Collections.singletonList(filePrefix + f.getName()) : aggregateProfilesInPath(basePath, filePrefix + f.getName()))
        .reduce(new ArrayList<>(), (a, b) -> {
          a.addAll(b);
          return a;
        });
  }

  @Data
  public static class ResolvedConfiguration {
    private Map<SpinnakerService.Type, Map<String, Profile>> serviceProfiles = new HashMap<>();
    SpinnakerRuntimeSettings runtimeSettings;

    public ServiceSettings getServiceSettings(SpinnakerService service) {
      return runtimeSettings.getServiceSettings(service);
    }

    public Map<String, Profile> getProfilesForService(SpinnakerService.Type type) {
      return serviceProfiles.getOrDefault(type, new HashMap<>());
    }
  }
}
