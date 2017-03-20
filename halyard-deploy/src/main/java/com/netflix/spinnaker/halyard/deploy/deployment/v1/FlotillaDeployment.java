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

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.provider.v1.ProviderInterface;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;

import java.util.concurrent.TimeUnit;

abstract public class FlotillaDeployment<T extends Account> extends Deployment {
  public FlotillaDeployment(AccountDeploymentDetails<T> deploymentDetails, ProviderInterface<T> providerInterface) {
    this.deploymentDetails = deploymentDetails;
    this.providerInterface = providerInterface;
  }

  @Override
  public DeploymentType deploymentType() {
    return DeploymentType.Flotilla;
  }

  @Override
  public SpinnakerEndpoints getEndpoints() {
    return deploymentDetails.getEndpoints();
  }

  private ProviderInterface<T> providerInterface;
  private AccountDeploymentDetails<T> deploymentDetails;

  private void waitForServiceUp(SpinnakerService service) {
    DaemonTaskHandler.log("Waiting for " + service.getArtifact().getName() + " to appear healthy");
    RunningServiceDetails details = getServiceDetails(service);
    while (details.getHealthy() == 0) {
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
      } catch (InterruptedException ignored) {
      }
      details = getServiceDetails(service);
    }
  }

  @Override
  public RemoteAction deploy(String spinnakerOutputPath) {
    SpinnakerEndpoints endpoints = getEndpoints();
    SpinnakerEndpoints.Services services = endpoints.getServices();
    DaemonTaskHandler.newStage("Bootstrapping a minimal Spinnaker installation");
    providerInterface.bootstrapSpinnaker(deploymentDetails, services);

    waitForServiceUp(services.getRedisBootstrap());
    waitForServiceUp(services.getOrcaBootstrap());
    waitForServiceUp(services.getClouddriverBootstrap());

    DaemonTaskHandler.newStage("Deploying remainder of Spinnaker services");
    OrcaService.Orca orca = providerInterface.connectTo(deploymentDetails, services.getOrcaBootstrap());
    providerInterface.ensureServiceIsRunning(deploymentDetails, services.getRedis());
    providerInterface.deployService(deploymentDetails, orca, endpoints, "clouddriver");
    providerInterface.deployService(deploymentDetails, orca, endpoints, "deck");
    providerInterface.deployService(deploymentDetails, orca, endpoints, "echo");
    providerInterface.deployService(deploymentDetails, orca, endpoints, "front50");
    providerInterface.deployService(deploymentDetails, orca, endpoints, "gate");
    providerInterface.deployService(deploymentDetails, orca, endpoints, "igor");
    providerInterface.deployService(deploymentDetails, orca, endpoints, "orca");
    providerInterface.deployService(deploymentDetails, orca, endpoints, "rosco");
    if (deploymentDetails.getDeploymentConfiguration().getSecurity().getAuthz().isEnabled()) {
      providerInterface.deployService(deploymentDetails, orca, endpoints, "fiat");
    }

    RemoteAction result = new RemoteAction();

    String deckConnection = providerInterface.connectToCommand(deploymentDetails, services.getDeck());
    String gateConnection = providerInterface.connectToCommand(deploymentDetails, services.getGate());
    result.setScript("#!/bin/bash\n" + deckConnection + "&\n" + gateConnection);
    result.setAutoRun(false);
    return result;
  }

  @Override
  public RunningServiceDetails getServiceDetails(SpinnakerService service) {
    return providerInterface.getRunningServiceDetails(deploymentDetails, service);
  }

  @Override
  public RemoteAction install(String spinnakerOutputPath) {
    throw new HalException(new ProblemBuilder(Problem.Severity.FATAL, "Cannot deploy Spinnaker remotely without configuration.").build());
  }
}
