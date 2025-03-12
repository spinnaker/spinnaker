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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.consul.ConsulClientProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.consul.ConsulServiceProfileFactoryBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.consul.ConsulApi;
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
public abstract class ConsulClientService extends SpinnakerService<ConsulApi>
    implements SidecarService {
  protected final String CLIENT_OUTPUT_PATH = "/etc/consul.d";

  @Autowired ConsulServiceProfileFactoryBuilder consulServiceProfileFactoryBuilder;

  @Autowired ConsulClientProfileFactory consulClientProfileFactory;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.CONSUL;
  }

  @Override
  public Type getType() {
    return Type.CONSUL_CLIENT;
  }

  @Override
  public Class<ConsulApi> getEndpointClass() {
    return ConsulApi.class;
  }

  public static String consulClientService(String serviceName) {
    return "consul/" + serviceName + ".json";
  }

  static String clientProfileName = "consul/client.json";

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    for (Map.Entry<Type, ServiceSettings> entry : endpoints.getAllServiceSettings().entrySet()) {
      ServiceSettings settings = entry.getValue();
      Type type = entry.getKey();
      if (!settings.getSidecar() && settings.getEnabled()) {
        String serviceName = type.getCanonicalName();
        String profileName = consulClientService(serviceName);
        String profilePath = Paths.get(CLIENT_OUTPUT_PATH, serviceName + ".json").toString();
        ProfileFactory factory = consulServiceProfileFactoryBuilder.build(type, settings);
        result.add(
            factory.getProfile(profileName, profilePath, deploymentConfiguration, endpoints));
      }
    }

    String profileName = clientProfileName;
    String profilePath = Paths.get(CLIENT_OUTPUT_PATH, profileName.split("/")[1]).toString();

    result.add(
        consulClientProfileFactory.getProfile(
            profileName, profilePath, deploymentConfiguration, endpoints));
    return result;
  }

  @Override
  public List<Profile> getSidecarProfiles(
      GenerateService.ResolvedConfiguration resolvedConfiguration, SpinnakerService service) {
    List<Profile> result = new ArrayList<>();
    Map<String, Profile> profiles = resolvedConfiguration.getProfilesForService(getType());
    Profile profile = profiles.get(consulClientService(service.getCanonicalName()));
    result.add(profile);
    profile = profiles.get(clientProfileName);
    result.add(profile);
    return result;
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends ServiceSettings {
    Integer port = 8500;
    // Address is how the service is looked up.
    String address = "localhost";
    // Host is what's bound to by the service.
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

    public Settings() {}
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    return Optional.empty();
  }
}
