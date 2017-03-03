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
 */

package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import com.netflix.spinnaker.halyard.core.resource.v1.JarResource;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints.Service;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.EndpointType;

import java.util.HashMap;
import java.util.Map;

public class LocalhostDebianDeployment extends Deployment {
  final DeploymentDetails deploymentDetails;

  public LocalhostDebianDeployment(DeploymentDetails deploymentDetails) {
    this.deploymentDetails = deploymentDetails;
  }

  @Override
  public DeploymentType deploymentType() {
    return DeploymentType.LocalhostDebian;
  }

  @Override
  public Object getService(EndpointType type) {
    String endpoint;
    switch (type) {
      case CLOUDDRIVER:
        Service clouddriver = getEndpoints().getServices().getClouddriver();
        endpoint = clouddriver.getAddress() + ":" + clouddriver.getPort();
        break;
      default:
        throw new IllegalArgumentException("Service for " + type + " not found");
    }

    return serviceFactory.createService(endpoint, type);
  }

  @Override
  public SpinnakerEndpoints getEndpoints() {
    return deploymentDetails.getEndpoints();
  }

  @Override
  public DeployResult deploy(String spinnakerOutputPath) {
    DaemonTaskHandler.newStage("Generating install file for Spinnaker debians");

    String pinFiles = "";
    String artifacts = "";

    DaemonTaskHandler.log("Collecting desired Spinnaker artifact versions");
    for (SpinnakerArtifact artifact : SpinnakerArtifact.values()) {
      if (!artifact.isSpinnakerInternal()) {
        continue;
      }

      JarResource pinFile = new JarResource("/debian/pin.sh");
      Map<String, String> bindings = new HashMap<>();
      String artifactName = artifact.getName();

      bindings.put("artifact", artifactName);
      bindings.put("version", deploymentDetails.getGenerateResult().getArtifactVersions().get(artifact));
      pinFiles += pinFile.setBindings(bindings).toString();

      artifacts += "\"" + artifactName + "\" ";
    }

    DaemonTaskHandler.log("Writing upstart and apt-preferences entries");

    JarResource etcInitResource = new JarResource("/debian/init.sh");
    Map<String, String> bindings = new HashMap<>();
    bindings.put("spinnaker-artifacts", artifacts.replace("deck", "apache2"));
    String etcInit = etcInitResource.setBindings(bindings).toString();

    DaemonTaskHandler.log("Writing installation file");
    JarResource installScript = new JarResource("/debian/install.sh");
    bindings = new HashMap<>();
    bindings.put("pin-files", pinFiles);
    bindings.put("spinnaker-artifacts", artifacts);
    bindings.put("install-redis", "true");
    bindings.put("install-spinnaker", "true");
    bindings.put("etc-init", etcInit);
    bindings.put("config-dir", spinnakerOutputPath);

    DeployResult result = new DeployResult();
    result.setPostInstallScript(installScript.setBindings(bindings).toString());
    result.setScriptDescription("Run this script on any machine you want to install Spinnaker on.");
    result.setPostInstallMessage("Halyard has generated an install script for you to run as "
        + "root on the machine the Halyard daemon is on. Halyard will not run this install script itself "
        + "since it does not have the necessary permissions to do so.");

    return result;
  }
}
