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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.SpinnakerProfileFactory;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Data
@Component
@EqualsAndHashCode(callSuper = true)
public abstract class SpringService<T> extends SpinnakerService<T> {
  protected String getConfigOutputPath() {
    return "/opt/spinnaker/config/";
  }

  @Autowired SpinnakerProfileFactory spinnakerProfileFactory;

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    String filename = "spinnaker.yml";
    String path = Paths.get(getConfigOutputPath(), filename).toString();
    List<Profile> result = new ArrayList<>();
    result.add(
        spinnakerProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints));

    if (hasServiceOverrides(deploymentConfiguration)) {
      String overridesFilename = getCanonicalName() + "-overrides.yml";
      String overridesPath = Paths.get(getConfigOutputPath(), overridesFilename).toString();
      result.add(
          spinnakerProfileFactory.getProfile(
              overridesFilename,
              overridesPath,
              deploymentConfiguration,
              getServiceOverrides(deploymentConfiguration, endpoints)));
    }

    return result;
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    if (profileName.equals(getCanonicalName() + ".yml")
        || profileName.startsWith(getCanonicalName() + "-")
        || profileName.equals(getType().getBaseType().getCanonicalName() + "-local.yml")
        || profileName.startsWith("spinnaker")) {
      return Optional.of(Paths.get(getConfigOutputPath(), profileName).toString());
    } else {
      return Optional.empty();
    }
  }

  // Active profiles are stored in a linked list so that new profiles of subclasses can be appended
  // at the head or tail
  protected LinkedList<String> getActiveSpringProfiles(
      DeploymentConfiguration deploymentConfiguration) {
    LinkedList<String> profiles = new LinkedList<>();
    if (hasTypeModifier()) {
      profiles.add(getTypeModifier());
    }
    if (hasServiceOverrides(deploymentConfiguration)) {
      profiles.add(hasTypeModifier() ? getTypeModifier() + "-overrides" : "overrides");
    }
    profiles.add("local");
    if (hasTypeModifier()) {
      profiles.add(getTypeModifier() + "-local");
    }
    return profiles;
  }

  protected boolean hasServiceOverrides(DeploymentConfiguration deploymentConfiguration) {
    return false;
  }

  protected List<Type> overrideServiceEndpoints() {
    return Collections.emptyList();
  }

  protected SpinnakerRuntimeSettings getServiceOverrides(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    return endpoints.newServiceOverrides(overrideServiceEndpoints());
  }
}
