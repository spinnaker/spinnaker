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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.vault.VaultMountConfigProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.vault.VaultMountGoogleConfigProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.vault.VaultStartupProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.VaultClientService;
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
public class BakeDebianVaultClientService extends VaultClientService
    implements BakeDebianService<VaultClientService.Vault> {
  final String upstartServiceName = null;

  StartupPriority priority = new StartupPriority(StartupPriority.HIGH);

  @Autowired ArtifactService artifactService;

  @Autowired VaultMountConfigProfileFactory mountConfigProfileFactory;

  @Autowired VaultMountGoogleConfigProfileFactory mountGoogleConfigProfileFactory;

  @Autowired VaultStartupProfileFactory vaultStartupProfileFactory;

  @Autowired String startupScriptPath;

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings().setArtifactId("vault").setEnabled(true);
  }

  @Override
  public String getStartupCommand() {
    return Paths.get(startupScriptPath, "startup-vault.sh").toString() + " \\$@";
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    String name = "mount-config.py";
    String path = Paths.get(startupScriptPath, name).toString();
    result.add(
        mountConfigProfileFactory.getProfile(name, path, deploymentConfiguration, endpoints));
    name = "startup-vault.sh";
    path = Paths.get(startupScriptPath, name).toString();
    result.add(
        vaultStartupProfileFactory.getProfile(name, path, deploymentConfiguration, endpoints));
    name = "google/mount-config.sh";
    path = Paths.get(startupScriptPath, name).toString();
    result.add(
        mountGoogleConfigProfileFactory.getProfile(name, path, deploymentConfiguration, endpoints));
    return result;
  }

  @Override
  public String installArtifactCommand(DeploymentDetails deploymentDetails) {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put("version", deploymentDetails.getArtifactVersion(getArtifact().getName()));
    return new StringReplaceJarResource("/services/vault/client/install.sh")
        .setBindings(bindings)
        .toString();
  }
}
