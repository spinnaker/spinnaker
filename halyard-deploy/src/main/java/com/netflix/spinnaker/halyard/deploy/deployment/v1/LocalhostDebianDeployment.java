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

import com.netflix.spinnaker.halyard.config.config.v1.AtomicFileWriter;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints.Service;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.EndpointType;

import java.io.IOException;

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
  public void deploy() {
    String pinFormat = "Package: spinnaker-%s\n"
        + "Pin: version %s\n"
        + "Pin-Priority: 1001\n";

    StringBuilder pinContents = new StringBuilder();

    deploymentDetails.getGenerateResult().getArtifactVersion().forEach((k, v) -> {
      pinContents.append(String.format(pinFormat, k.getName(), v));
    });

    AtomicFileWriter fileWriter = null;

    try {
      fileWriter = new AtomicFileWriter("/etc/apt/preferences.d/pin-spin");
      fileWriter.write(pinContents.toString());
    } catch (IOException e) {
      throw new HalException(new ConfigProblemBuilder(Severity.ERROR, "Failed to write debian pin file: " + e).build());
    } finally {
      if (fileWriter != null) {
        fileWriter.close();
      }
    }
  }
}
