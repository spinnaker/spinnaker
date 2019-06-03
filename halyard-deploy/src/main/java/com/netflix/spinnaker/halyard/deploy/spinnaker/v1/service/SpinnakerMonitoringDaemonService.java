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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.MetricRegistryProfileFactoryBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.SpinnakerMonitoringDaemonProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService;
import java.nio.file.Paths;
import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public abstract class SpinnakerMonitoringDaemonService
    extends SpinnakerService<SpinnakerMonitoringDaemonService.SpinnakerMonitoringDaemon>
    implements SidecarService {
  protected final String CONFIG_OUTPUT_PATH = "/opt/spinnaker-monitoring/config/";
  protected final String REGISTRY_OUTPUT_PATH = "/opt/spinnaker-monitoring/registry/";
  protected final String FILTERS_OUTPUT_PATH = "/opt/spinnaker-monitoring/filters/";

  @Autowired SpinnakerMonitoringDaemonProfileFactory spinnakerMonitoringDaemonProfileFactory;

  @Autowired MetricRegistryProfileFactoryBuilder metricRegistryProfileFactoryBuilder;

  @Autowired List<SpinnakerService> services;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.SPINNAKER_MONITORING_DAEMON;
  }

  @Override
  public Type getType() {
    return Type.MONITORING_DAEMON;
  }

  @Override
  public Class<SpinnakerMonitoringDaemon> getEndpointClass() {
    return SpinnakerMonitoringDaemon.class;
  }

  private static String serviceRegistryProfileName(String serviceName) {
    return "registry/" + serviceName + ".yml";
  }

  private static String monitoringProfileName() {
    return "spinnaker-monitoring.yml";
  }

  private static String monitoringLocalProfileName() {
    return "spinnaker-monitoring-local.yml";
  }

  private static String defaultFilterProfileName() {
    return "monitoring-daemon/filters/default.yml";
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> results = new ArrayList<>();
    for (Map.Entry<Type, ServiceSettings> entry : endpoints.getAllServiceSettings().entrySet()) {
      ServiceSettings settings = entry.getValue();
      if (settings.getMonitored() && settings.getEnabled()) {
        String serviceName = entry.getKey().getCanonicalName();
        String profileName = serviceRegistryProfileName(serviceName);
        String profilePath = Paths.get(REGISTRY_OUTPUT_PATH, serviceName + ".yml").toString();
        ProfileFactory factory = metricRegistryProfileFactoryBuilder.build(settings);
        results.add(
            factory.getProfile(profileName, profilePath, deploymentConfiguration, endpoints));
      }
    }

    String profileName = monitoringProfileName();
    String profilePath = Paths.get(CONFIG_OUTPUT_PATH, profileName).toString();

    results.add(
        spinnakerMonitoringDaemonProfileFactory.getProfile(
            profileName, profilePath, deploymentConfiguration, endpoints));

    return results;
  }

  @Override
  public List<Profile> getSidecarProfiles(
      GenerateService.ResolvedConfiguration resolvedConfiguration, SpinnakerService service) {
    List<Profile> result = new ArrayList<>();
    Map<String, Profile> monitoringProfiles =
        resolvedConfiguration.getProfilesForService(getType());

    String profileName = serviceRegistryProfileName(service.getCanonicalName());
    Profile profile = monitoringProfiles.get(profileName);
    result.add(profile);

    profile = monitoringProfiles.get(monitoringProfileName());
    result.add(profile);

    profile = monitoringProfiles.get(monitoringLocalProfileName());
    if (profile != null) {
      result.add(profile);
    }

    profile = monitoringProfiles.get(defaultFilterProfileName());
    if (profile != null) {
      result.add(profile);
    }

    return result;
  }

  public interface SpinnakerMonitoringDaemon {}

  @EqualsAndHashCode(callSuper = true)
  @Data
  public class Settings extends ServiceSettings {
    Integer port = 8008;
    String address = "localhost";
    String host = "0.0.0.0";
    String scheme = "http";
    String healthEndpoint = null;
    Boolean enabled = true;
    Boolean safeToUpdate = true;
    Boolean monitored = false;
    Boolean sidecar = true;
    Integer targetSize = 1;
    Boolean skipLifeCycleManagement = false;
    Map<String, String> env = new HashMap<>();
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    if (defaultFilterProfileName().equalsIgnoreCase(profileName)) {
      return Optional.of(Paths.get(FILTERS_OUTPUT_PATH, "default.yml").toString());
    }
    if (monitoringLocalProfileName().equalsIgnoreCase(profileName)) {
      return Optional.of(Paths.get(CONFIG_OUTPUT_PATH, profileName).toString());
    }
    return Optional.empty();
  }
}
