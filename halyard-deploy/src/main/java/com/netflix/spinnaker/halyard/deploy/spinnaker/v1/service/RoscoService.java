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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.RegistryBackedArchiveProfileBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.RoscoProfileFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.http.GET;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
abstract public class RoscoService extends SpringService<RoscoService.Rosco> {
  @Autowired
  RoscoProfileFactory roscoProfileFactory;

  @Autowired
  RegistryBackedArchiveProfileBuilder prefixProfileBuilder;

  protected String getRoscoConfigPath() {
    return "/opt/rosco/config";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.ROSCO;
  }

  @Override
  public Type getType() {
    return Type.ROSCO;
  }

  @Override
  public Class<Rosco> getEndpointClass() {
    return Rosco.class;
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    Optional<String> result = super.customProfileOutputPath(profileName);
    if (!result.isPresent()) {
      if (profileName.startsWith("rosco/")) {
        return Optional.of(getRoscoConfigPath() + profileName.substring("rosco".length()));
      }
    }

    return result;
  }

  protected void appendCustomConfigDir(Profile profile) {
  }

  @Override
  public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);
    String filename = "rosco.yml";

    String path = Paths.get(getConfigOutputPath(), filename).toString();
    Profile profile = roscoProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints);

    appendCustomConfigDir(profile);

    profiles.add(profile);
    profiles.addAll(prefixProfileBuilder.build(deploymentConfiguration, getRoscoConfigPath(), getArtifact(), "packer"));
    return profiles;
  }

  public interface Rosco {
    @GET("/resolvedEnv")
    Map<String, String> resolvedEnv();

    @GET("/health")
    SpringHealth health();

    @GET("/status/all")
    AllStatus getAllStatus();

    @Data
    class AllStatus {
      String instance;
      Map<String, InstanceStatus> instances;
    }

    @Data
    class InstanceStatus {
      Status status;
      List<Map> bakes;
    }

    enum Status {
      RUNNING,
      IDLE
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends SpringServiceSettings {
    Integer port = 8087;
    // Address is how the service is looked up.
    String address = "localhost";
    // Host is what's bound to by the service.
    String host = "0.0.0.0";
    String scheme = "http";
    String healthEndpoint = "/health";
    Boolean enabled = true;
    Boolean safeToUpdate = true;
    Boolean monitored = true;
    Boolean sidecar = false;
    Integer targetSize = 1;
    Boolean skipLifeCycleManagement = false;
    Map<String, String> env = new HashMap<>();

    public Settings() {}

    public Settings(List<String> profiles) {
      setProfiles(profiles);
    }
  }
}
