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
import com.netflix.spinnaker.halyard.config.model.v1.security.ApiSecurity;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateBoot128ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateBoot154ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import java.util.Collections;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
@Component
abstract public class GateService extends SpringService<GateService.Gate> {

  private static final String BOOT_UPGRADED_VERSION = "0.7.0";

  @Autowired
  private GateBoot154ProfileFactory boot154ProfileFactory;

  @Autowired
  private GateBoot128ProfileFactory boot128ProfileFactory;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.GATE;
  }

  @Override
  public Type getType() {
    return Type.GATE;
  }

  @Override
  public Class<Gate> getEndpointClass() {
    return Gate.class;
  }

  @Override
  public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);
    String filename = "gate.yml";

    String path = Paths.get(getConfigOutputPath(), filename).toString();
    GateProfileFactory gateProfileFactory = getGateProfileFactory(deploymentConfiguration.getName());
    Profile profile = gateProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints);

    profiles.add(profile);
    return profiles;
  }

  private GateProfileFactory getGateProfileFactory(String deploymentName) {
    String version = getArtifactService().getArtifactVersion(deploymentName, SpinnakerArtifact.GATE);
    try {
      if (Versions.lessThan(version, BOOT_UPGRADED_VERSION)) {
        return boot128ProfileFactory;
      }
    } catch (NumberFormatException nfe) {
      log.warn("Could not resolve Gate version, using `boot154ProfileFactory`.");
    }
    return boot154ProfileFactory;
  }

  public GateService() {
    super();
  }

  public interface Gate { }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends SpringServiceSettings {
    Integer port = 8084;
    String address = "localhost";
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

    public Settings(ApiSecurity apiSecurity) {
      this(apiSecurity, Collections.emptyList());
    }

    public Settings(ApiSecurity apiSecurity, List<String> profiles) {
      setProfiles(profiles);
      setOverrideBaseUrl(apiSecurity.getOverrideBaseUrl());
      if (apiSecurity.getSsl().isEnabled()) {
        scheme = "https";
      }
    }
  }
}
