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
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.resource.v1.JarResource;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ClouddriverService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerPublicService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import retrofit.RetrofitError;

import java.util.HashMap;
import java.util.Map;

public class LocalhostDebianDeployment extends Deployment {
  final DeploymentDetails deploymentDetails;
  final String repository;

  public LocalhostDebianDeployment(DeploymentDetails deploymentDetails, String repository) {
    this.deploymentDetails = deploymentDetails;
    this.repository = repository;
  }

  @Override
  public DeploymentType deploymentType() {
    return DeploymentType.LocalhostDebian;
  }

  @Override
  public RunningServiceDetails getServiceDetails(SpinnakerService service) {
    RunningServiceDetails details = new RunningServiceDetails();

    if (service instanceof SpinnakerPublicService) {
      details.setPublicService((SpinnakerPublicService) service);
    } else {
      details.setService(service);
    }

    String endpoint = service.getBaseUrl();
    Object serviceInterface = serviceInterfaceFactory.createService(endpoint, service);
    SpinnakerArtifact artifact = service.getArtifact();
    boolean healthy = false;

    try {
      switch (artifact) {
        case CLOUDDRIVER:
          ClouddriverService.Clouddriver clouddriver = (ClouddriverService.Clouddriver) serviceInterface;
          healthy = clouddriver.health().getStatus().equals("UP");
          break;
        default:
          throw new HalException(
            new ProblemBuilder(Problem.Severity.FATAL, "Service " + artifact.getName() + " cannot be inspected.").build()
          );
      }
    } catch (RetrofitError e) {
      // Do nothing, service isn't healthy
    }

    details.setHealthy(healthy ? 1 : 0);
    details.setVersion(deploymentDetails.getGenerateResult().getArtifactVersion(artifact));

    return details;
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
    bindings.put("install-java", "true");
    bindings.put("install-spinnaker", "true");
    bindings.put("etc-init", etcInit);
    bindings.put("config-dir", spinnakerOutputPath);
    bindings.put("debian-repo", repository);

    DeployResult result = new DeployResult();
    result.setPostInstallScript(installScript.setBindings(bindings).toString());
    result.setScriptDescription("Run this script on any machine you want to install Spinnaker on.");
    result.setPostInstallMessage("Halyard has generated an install script for you to run as "
        + "root on the machine the Halyard daemon is on. Halyard will not run this install script itself "
        + "since it does not have the necessary permissions to do so.");

    return result;
  }
}
