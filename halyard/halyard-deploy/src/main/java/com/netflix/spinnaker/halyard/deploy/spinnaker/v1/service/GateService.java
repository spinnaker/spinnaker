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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateBoot667ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.GateProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
@Component
public abstract class GateService extends SpringService<GateService.Gate> {

  private static final String BOOT_UPGRADED_VERSION = "0.7.0";

  @Autowired private GateBoot154ProfileFactory boot154ProfileFactory;

  @Autowired private GateBoot128ProfileFactory boot128ProfileFactory;

  @Autowired private GateBoot667ProfileFactory boot667ProfileFactory;

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

  protected void appendReadonlyClouddriverForDeck(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {}

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);
    String filename = "gate.yml";

    String path = Paths.get(getConfigOutputPath(), filename).toString();
    GateProfileFactory gateProfileFactory =
        getGateProfileFactory(deploymentConfiguration.getName());
    Profile profile =
        gateProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints);

    appendReadonlyClouddriverForDeck(profile, deploymentConfiguration, endpoints);

    profiles.add(profile);
    return profiles;
  }

  /**
   * Retrieves the appropriate GateProfileFactory based on the given deployment's Gate version.
   *
   * <p>- If version is less than 0.7.0, returns {@code boot128ProfileFactory}. - If version is
   * between 0.7.0 and 6.67.0, returns {@code boot154ProfileFactory}. - If version is greater than
   * 6.67.0 or invalid, defaults to {@code boot667ProfileFactory}.
   *
   * @param deploymentName Name of the deployment.
   * @return The appropriate {@link GateProfileFactory} instance.
   */
  GateProfileFactory getGateProfileFactory(String deploymentName) {
    String version =
        getArtifactService().getArtifactVersion(deploymentName, SpinnakerArtifact.GATE);
    log.info("the current spinnaker version is: " + version);
    try {
      if (Versions.lessThan(version, BOOT_UPGRADED_VERSION)) {
        return boot128ProfileFactory;
      }

      // For Gate versions 6.67.0 and above, a different set of properties is required to enable
      // OAuth2.
      // Therefore, boot154ProfileFactory is not used, and boot667ProfileFactory is chosen instead.
      if (Versions.lessThan(version, "6.67.0")) {
        return boot154ProfileFactory;
      }
    } catch (IllegalArgumentException iae) {
      log.warn("Could not resolve Gate version, using `boot154ProfileFactory`.");
    }
    return boot667ProfileFactory;
  }

  public GateService() {
    super();
  }

  public interface Gate {}

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
