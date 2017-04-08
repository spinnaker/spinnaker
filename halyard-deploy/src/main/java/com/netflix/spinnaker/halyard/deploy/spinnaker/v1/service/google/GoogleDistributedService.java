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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.google;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.services.v1.VaultService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.*;
import org.apache.commons.lang.RandomStringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public interface GoogleDistributedService<T> extends DistributedService<T, GoogleAccount> {
  VaultService getVaultService();

  default List<ConfigSource> stageProfiles(AccountDeploymentDetails<GoogleAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration) {
    Integer version = getLatestEnabledServiceVersion(details);
    if (version == null) {
      version = 0;
    } else {
      version++;
    }

    SpinnakerService thisService = getService();
    ServiceSettings thisServiceSettings = resolvedConfiguration.getServiceSettings(thisService);
    SpinnakerMonitoringDaemonService monitoringService = getMonitoringDaemonService();
    String name = getName();
    List<ConfigSource> configSources = new ArrayList<>();
    ServiceSettings monitoringSettings = resolvedConfiguration.getServiceSettings(monitoringService);
    String stagingPath = getSpinnakerStagingPath();
    VaultService vaultService = getVaultService();
    DeploymentConfiguration deploymentConfiguration = details.getDeploymentConfiguration();

    if (thisServiceSettings.isMonitored() && monitoringSettings.isEnabled()) {
      Map<String, Profile> monitoringProfiles = resolvedConfiguration.getProfilesForService(monitoringService.getType());

      Profile profile = monitoringProfiles.get(SpinnakerMonitoringDaemonService.serviceRegistryProfileName(name));
      if (profile == null) {
        throw new RuntimeException("Assertion violated: service monitoring enabled but no registry entry generated.");
      }

      String secretName = secretName(profile.getName(), version);
      String mountPoint = Paths.get(profile.getOutputFile()).toString();
      Path stagedFile = Paths.get(profile.getStagedFile(stagingPath));
      vaultService.publishSecret(deploymentConfiguration, secretName, stagedFile);

      configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));

      profile = monitoringProfiles.get("monitoring.yml");
      if (profile == null) {
        throw new RuntimeException("Assertion violated: service monitoring enabled but no monitoring profile was generated.");
      }

      secretName = secretName(profile.getName(), version);
      mountPoint = Paths.get(profile.getOutputFile()).toString();
      stagedFile = Paths.get(profile.getStagedFile(stagingPath));
      vaultService.publishSecret(deploymentConfiguration, secretName, stagedFile);

      configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));
    }

    Map<String, Profile> serviceProfiles = resolvedConfiguration.getProfilesForService(thisService.getType());
    Set<String> requiredFiles = new HashSet<>();

    for (Map.Entry<String, Profile> entry : serviceProfiles.entrySet()) {
      Profile profile = entry.getValue();
      requiredFiles.addAll(profile.getRequiredFiles());

      String mountPoint = profile.getOutputFile();
      String secretName = secretName("profile-" + profile.getName(), version);
      Path stagedFile = Paths.get(profile.getStagedFile(stagingPath));
      vaultService.publishSecret(deploymentConfiguration, secretName, stagedFile);

      configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));
    }

    for (String file : requiredFiles) {
      String mountPoint = Paths.get(file).toString();
      String secretName = secretName("dependencies-" + file, version);
      vaultService.publishSecret(deploymentConfiguration, secretName, Paths.get(file));

      configSources.add(new ConfigSource().setId(secretName).setMountPath(mountPoint));
    }

    return configSources;
  }

  default String secretName(String detail, int version) {
    return String.join("-",
        "hal",
        getService().getType().getCanonicalName(),
        detail,
        version + "",
        RandomStringUtils.random(5, true, true));
  }
}
