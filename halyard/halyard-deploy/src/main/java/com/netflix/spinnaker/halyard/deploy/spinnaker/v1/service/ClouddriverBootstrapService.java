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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ClouddriverBootstrapProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class ClouddriverBootstrapService extends ClouddriverService {
  final boolean monitored = false;
  final boolean sidecar = false;

  @Autowired ClouddriverBootstrapProfileFactory clouddriverBootstrapProfileFactory;

  @Override
  public Type getType() {
    return Type.CLOUDDRIVER_BOOTSTRAP;
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);

    // Due to a "feature" in how spring merges profiles, list entries (including
    // requiredGroupMembership) are
    // merged rather than overwritten. Including the base profile will prevent fiat-enabled setups
    // from deploying
    // anything since the deploying account will be restricted from performing any operations
    profiles =
        profiles.stream()
            .filter(p -> !p.getName().equals("clouddriver.yml"))
            .collect(Collectors.toList());

    String filename = "clouddriver-bootstrap.yml";
    String path = Paths.get(getConfigOutputPath(), filename).toString();
    Profile profile =
        clouddriverBootstrapProfileFactory.getProfile(
            filename, path, deploymentConfiguration, endpoints);

    profiles.add(profile);
    return profiles;
  }
}
