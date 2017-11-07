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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.core.resource.v1.JarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalService;

import java.util.HashMap;
import java.util.Map;

public interface LocalGitService<T> extends LocalService<T> {
  ArtifactService getArtifactService();
  default String getDefaultHost() {
    return "localhost";
  }

  default String getHomeDirectory() {
    return System.getProperty("user.home");
  }

  default String getArtifactId(String deploymentName) {
    return String.join("@", getArtifact().getName(), getArtifactCommit(deploymentName));
  }

  default String getArtifactCommit(String deploymentName) {
    SpinnakerArtifact artifact = getArtifact();
    return getArtifactService().getArtifactCommit(deploymentName, artifact);
  }

  default String installArtifactCommand(DeploymentDetails deploymentDetails) {
    Map<String, String> bindings = new HashMap<>();
    String artifactName = getArtifact().getName();
    bindings.put("artifact", artifactName);
    bindings.put("version", getArtifactCommit(deploymentDetails.getDeploymentName()));

    DeploymentEnvironment.GitConfig gitConfig = deploymentDetails.getDeploymentConfiguration()
        .getDeploymentEnvironment()
        .getGitConfig();

    bindings.put("origin", gitConfig.getOriginUser());
    bindings.put("upstream", gitConfig.getUpstreamUser());

    TemplatedResource installResource = new JarResource("/git/install-component.sh");

    installResource.setBindings(bindings);

    return installResource.toString();
  }
}
