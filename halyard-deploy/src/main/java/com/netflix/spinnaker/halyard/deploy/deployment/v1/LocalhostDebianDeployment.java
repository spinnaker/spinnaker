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
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.resource.v1.JarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ClouddriverService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerPublicService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import retrofit.RetrofitError;

import java.util.*;

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
    details.setVersion(deploymentDetails.getBillOfMaterials().getArtifactVersion(artifact.getName()));

    return details;
  }

  @Override
  public SpinnakerEndpoints getEndpoints() {
    return deploymentDetails.getEndpoints();
  }

  @Override
  public RemoteAction deploy(String spinnakerOutputPath) {
    Map<String, String> bindings = new HashMap<>();
    bindings.put("config-dir", spinnakerOutputPath);
    bindings.put("service-action", "restart");
    RemoteAction result = new RemoteAction();
    List<SpinnakerArtifact> artifacts = Arrays.asList(SpinnakerArtifact.values());
    result.setScript(installScript(artifacts).extendBindings(bindings).toString());
    result.setAutoRun(true);

    return result;
  }

  @Override
  public RemoteAction install(String spinnakerOutputPath) {
    Map<String, String> bindings = new HashMap<>();
    bindings.put("config-dir", "");
    bindings.put("service-action", "stop");
    RemoteAction result = new RemoteAction();
    List<SpinnakerArtifact> artifacts = Arrays.asList(SpinnakerArtifact.values());
    result.setScript(installScript(artifacts).extendBindings(bindings).toString());
    result.setAutoRun(true);

    return result;
  }

  private TemplatedResource installScript(Collection<SpinnakerArtifact> spinnakerArtifacts) {
    DaemonTaskHandler.newStage("Generating install script for Spinnaker debians");
    BillOfMaterials billOfMaterials = deploymentDetails.getBillOfMaterials();

    String pinFiles = "";
    String artifacts = "";

    DaemonTaskHandler.log("Collecting desired Spinnaker artifact versions");
    for (SpinnakerArtifact artifact : spinnakerArtifacts) {
      if (!artifact.isSpinnakerInternal()) {
        continue;
      }

      JarResource pinFile = new JarResource("/debian/pin.sh");
      Map<String, String> bindings = new HashMap<>();
      String artifactName = artifact.getName();

      bindings.put("artifact", artifactName);
      bindings.put("version", billOfMaterials.getArtifactVersion(artifactName));
      pinFiles += pinFile.setBindings(bindings).toString();

      artifacts += "\"" + artifactName + "\" ";
    }

    DaemonTaskHandler.log("Writing upstart and apt-preferences entries");

    JarResource etcInitResource = new JarResource("/debian/init.sh");
    Map<String, String> bindings = new HashMap<>();
    bindings.put("spinnaker-artifacts", artifacts.replace("deck", "apache2").replace("monitoring-daemon", "spinnaker-monitoring"));
    String etcInit = etcInitResource.setBindings(bindings).toString();

    DaemonTaskHandler.log("Writing installation file");
    JarResource installScript = new JarResource("/debian/install.sh");
    bindings = new HashMap<>();
    bindings.put("pin-files", pinFiles);
    bindings.put("spinnaker-artifacts", artifacts);
    bindings.put("etc-init", etcInit);
    bindings.put("debian-repo", repository);
    bindings.put("install-redis", "true");
    bindings.put("install-java", "true");
    bindings.put("install-spinnaker", "true");

    return installScript.setBindings(bindings);
  }
}
