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

import com.netflix.spinnaker.halyard.core.resource.v1.StringReplaceJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.BakeService;
import java.util.HashMap;
import java.util.Map;

public interface BakeDebianService<T> extends BakeService<T> {
  ArtifactService getArtifactService();

  String getUpstartServiceName();

  default String getHomeDirectory() {
    return "/home/spinnaker";
  }

  @Override
  default String getStartupCommand() {
    if (getUpstartServiceName() != null) {
      return "service " + getUpstartServiceName() + " restart";
    }

    return null;
  }

  default String getArtifactId(String deploymentName) {
    SpinnakerArtifact artifact = getArtifact();
    String version = getArtifactService().getArtifactVersion(deploymentName, artifact);
    return String.format("spinnaker-%s=%s", artifact.getName(), version);
  }

  default String installArtifactCommand(DeploymentDetails deploymentDetails) {
    Map<String, Object> bindings = new HashMap<>();
    String artifactName = getArtifact().getName();
    bindings.put("artifact", artifactName);
    bindings.put("version", deploymentDetails.getArtifactVersion(artifactName));

    // pin as well as install at a particular version to ensure `apt-get uprade` doesn't
    // accidentally upgrade to `nightly`
    TemplatedResource pinResource = new StringReplaceJarResource("/debian/pin.sh");
    TemplatedResource installResource =
        new StringReplaceJarResource("/debian/install-component.sh");
    String ensureStopped =
        String.join("\n", "set +e", "service " + getUpstartServiceName() + " stop", "set -e");

    pinResource.setBindings(bindings);
    installResource.setBindings(bindings);

    return String.format("%s\n%s\n%s", pinResource, installResource, ensureStopped);
  }
}
