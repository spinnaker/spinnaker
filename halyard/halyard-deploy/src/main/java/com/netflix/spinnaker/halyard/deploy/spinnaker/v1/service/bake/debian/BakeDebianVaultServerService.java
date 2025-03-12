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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.vault.VaultMountConfigProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.vault.VaultMountGoogleConfigProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.vault.VaultStartupProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.VaultServerService;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class BakeDebianVaultServerService extends VaultServerService
    implements BakeDebianService<VaultServerService.Vault> {
  final String upstartServiceName = "vault";

  StartupPriority priority = new StartupPriority(StartupPriority.LOW);

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
  public String installArtifactCommand(DeploymentDetails deploymentDetails) {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put("version", deploymentDetails.getArtifactVersion(getArtifact().getName()));
    return new StringReplaceJarResource("/services/vault/server/install.sh")
        .setBindings(bindings)
        .toString();
  }
}
