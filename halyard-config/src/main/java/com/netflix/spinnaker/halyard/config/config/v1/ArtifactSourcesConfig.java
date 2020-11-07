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

package com.netflix.spinnaker.halyard.config.config.v1;

import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties("spinnaker.artifacts")
@Configuration
public class ArtifactSourcesConfig {
  String gitPrefix = "https://github.com/spinnaker";
  String googleImageProject = "marketplace-spinnaker-release";
  String dockerRegistry = "us-docker.pkg.dev/spinnaker-community/docker";
  String debianRepository = "https://dl.bintray.com/spinnaker-releases/debians";

  public ArtifactSourcesConfig mergeWithBomSources(
      BillOfMaterials.ArtifactSources artifactSources) {
    if (artifactSources == null) {
      return this;
    }

    if (StringUtils.isNotEmpty(artifactSources.getGoogleImageProject())) {
      googleImageProject = artifactSources.getGoogleImageProject();
    }

    if (StringUtils.isNotEmpty(artifactSources.getDockerRegistry())) {
      dockerRegistry = artifactSources.getDockerRegistry();
    }

    if (StringUtils.isNotEmpty(artifactSources.getDebianRepository())) {
      debianRepository = artifactSources.getDebianRepository();
    }

    if (StringUtils.isNotEmpty(artifactSources.getDebianRepository())) {
      gitPrefix = artifactSources.getGitPrefix();
    }

    return this;
  }
}
