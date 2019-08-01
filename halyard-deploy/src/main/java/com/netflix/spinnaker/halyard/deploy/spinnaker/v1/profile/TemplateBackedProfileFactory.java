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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.resource.v1.StringResource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class TemplateBackedProfileFactory extends ProfileFactory {
  protected abstract String getTemplate();

  protected abstract Map<String, Object> getBindings(
      DeploymentConfiguration deploymentConfiguration,
      Profile profile,
      SpinnakerRuntimeSettings endpoints);

  protected List<String> requiredFiles(DeploymentConfiguration deploymentConfiguration) {
    return new ArrayList<>();
  }

  @Override
  protected Profile getBaseProfile(String name, String version, String outputFile) {
    return new Profile(name, version, outputFile, getTemplate());
  }

  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    StringResource template = new StringResource(profile.getBaseContents());
    profile.setRequiredFiles(requiredFiles(deploymentConfiguration));
    Map<String, Object> bindings = getBindings(deploymentConfiguration, profile, endpoints);
    profile.setContents(template.setBindings(bindings).toString());
  }
}
