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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.google;

import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSourcesConfig;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceInterfaceFactory;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GoogleDistributedServiceDelegate {
  @Autowired @Getter GoogleMonitoringDaemonService monitoringDaemonService;

  @Autowired @Getter ArtifactService artifactService;

  @Autowired @Getter ServiceInterfaceFactory serviceInterfaceFactory;

  @Autowired ArtifactSourcesConfig artifactSourcesConfig;

  @Autowired @Getter String startupScriptPath;

  @Autowired @Getter GoogleVaultClientService vaultClientService;

  @Autowired @Getter GoogleVaultServerService vaultServerService;

  @Autowired @Getter GoogleConsulClientService consulClientService;

  @Autowired @Getter GoogleConsulServerService consulServerService;

  public String getGoogleImageProject(String deploymentName, SpinnakerArtifact artifact) {
    BillOfMaterials.ArtifactSources artifactSources =
        artifactService.getArtifactSources(deploymentName, artifact);
    return artifactSourcesConfig.mergeWithBomSources(artifactSources).getGoogleImageProject();
  }
}
