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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.consul.ConsulApi;
import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public abstract class ConsulServerService extends SpinnakerService<ConsulApi> {
  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.CONSUL;
  }

  @Override
  public Type getType() {
    return Type.CONSUL_SERVER;
  }

  @Override
  public Class<ConsulApi> getEndpointClass() {
    return ConsulApi.class;
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    return new ArrayList<>();
  }

  public interface Consul {}

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
    Boolean safeToUpdate = false;
    Boolean monitored = false;
    Boolean sidecar = false;
    Integer targetSize = 3;
    Boolean skipLifeCycleManagement = false;
    Map<String, String> env = new HashMap<>();

    public Settings() {}
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    return Optional.empty();
  }
}
