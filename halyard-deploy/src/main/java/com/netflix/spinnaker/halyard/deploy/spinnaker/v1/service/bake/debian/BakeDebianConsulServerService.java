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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.debian;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.resource.v1.StringReplaceJarResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.consul.ConsulServerBootstrapGoogleProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.consul.ConsulServerStartupProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConsulServerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.consul.ConsulApi;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class BakeDebianConsulServerService extends ConsulServerService
    implements BakeDebianService<ConsulApi> {
  @Autowired ArtifactService artifactService;

  StartupPriority priority = new StartupPriority(StartupPriority.LOW);

  final String upstartServiceName = "consul";

  @Autowired String startupScriptPath;

  @Autowired ConsulServerStartupProfileFactory consulServerStartupProfileFactory;

  @Autowired ConsulServerBootstrapGoogleProfileFactory consulServerBootstrapGoogleProfileFactory;

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings().setArtifactId("consul").setEnabled(true);
  }

  @Override
  public String getStartupCommand() {
    return Paths.get(startupScriptPath, "startup-consul.sh").toString() + " \\$@";
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    String name = "startup-consul.sh";
    String path = Paths.get(startupScriptPath, name).toString();
    result.add(
        consulServerStartupProfileFactory.getProfile(
            name, path, deploymentConfiguration, endpoints));
    name = "google/bootstrap-consul.sh";
    path = Paths.get(startupScriptPath, name).toString();
    result.add(
        consulServerBootstrapGoogleProfileFactory.getProfile(
            name, path, deploymentConfiguration, endpoints));
    return result;
  }

  @Override
  public String installArtifactCommand(DeploymentDetails deploymentDetails) {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put("version", deploymentDetails.getArtifactVersion(getArtifact().getName()));
    return new StringReplaceJarResource("/services/consul/server/install.sh")
        .setBindings(bindings)
        .toString();
  }
}
