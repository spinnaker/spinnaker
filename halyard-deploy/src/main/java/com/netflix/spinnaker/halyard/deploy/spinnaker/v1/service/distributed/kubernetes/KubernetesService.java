/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes;

import static com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.ImageVariant.SLIM;

import com.google.common.base.Strings;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.ImageVariant;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;

public interface KubernetesService {
  String getDockerRegistry(String deploymentName, SpinnakerArtifact artifact);

  SpinnakerArtifact getArtifact();

  ArtifactService getArtifactService();

  default String getArtifactId(DeploymentConfiguration deploymentConfiguration) {
    String deploymentName = deploymentConfiguration.getName();
    String artifactName = getArtifact().getName();
    String version = getArtifactService().getArtifactVersion(deploymentName, getArtifact());
    version = Versions.isLocal(version) ? Versions.fromLocal(version) : version;

    ImageVariant imageVariant =
        deploymentConfiguration.getDeploymentEnvironment().getImageVariant();

    final String tag;
    if (imageVariant == SLIM) {
      // Keep using the variantless tag until `gs://halconfig/versions.yml` only contains
      // versions >= 1.16.0
      tag = version;
    } else {
      tag = String.format("%s-%s", version, imageVariant.getContainerSuffix());
    }

    String registry = getDockerRegistry(deploymentName, getArtifact());
    if (!Strings.isNullOrEmpty(registry)) {
      return String.format("%s/%s:%s", registry, artifactName, tag);
    } else {
      return String.format("%s:%s", artifactName, tag);
    }
  }
}
