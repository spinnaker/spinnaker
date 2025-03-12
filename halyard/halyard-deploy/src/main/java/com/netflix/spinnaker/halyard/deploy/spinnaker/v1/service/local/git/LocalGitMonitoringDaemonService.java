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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerMonitoringDaemonService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class LocalGitMonitoringDaemonService extends SpinnakerMonitoringDaemonService
    implements LocalGitService<SpinnakerMonitoringDaemonService.SpinnakerMonitoringDaemon> {
  final String upstartServiceName = "spinnaker-monitoring";
  final String pipRequirementsFile = "/opt/spinnaker-monitoring/requirements.txt";

  String startCommand = "";

  @Autowired String gitRoot;

  @Autowired ArtifactService artifactService;

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings()
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setHost(getDefaultHost())
        .setEnabled(deploymentConfiguration.getMetricStores().isEnabled());
  }

  @Override
  public String installArtifactCommand(DeploymentDetails deploymentDetails) {
    // TODO(brnelson): Clearly wrong...
    String installCommand = LocalGitService.super.installArtifactCommand(deploymentDetails);
    return String.join(
        "\n",
        installCommand,
        "apt-get install -y python-dev",
        "sed -i -e 's/#@ //g' " + pipRequirementsFile,
        "pip install -r " + pipRequirementsFile);
  }

  public String getArtifactId(String deploymentName) {
    return LocalGitService.super.getArtifactId(deploymentName);
  }

  @Override
  public void collectLogs(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new UnsupportedOperationException();
  }
}
