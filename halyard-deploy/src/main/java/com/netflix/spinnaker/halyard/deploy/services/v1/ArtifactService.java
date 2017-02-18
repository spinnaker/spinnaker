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

package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.netflix.spinnaker.halyard.config.config.v1.StrictObjectMapper;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.registry.ProfileRegistry;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.registry.WriteableProfileRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import retrofit.RetrofitError;

import java.io.IOException;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

@Component
public class ArtifactService {
  @Autowired
  ProfileRegistry profileRegistry;

  @Autowired(required = false)
  WriteableProfileRegistry writeableProfileRegistry;

  @Autowired
  Yaml yaml;

  @Autowired
  StrictObjectMapper strictObjectMapper;

  @Autowired
  DeploymentService deploymentService;

  public BillOfMaterials getBillOfMaterials(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    String version = deploymentConfiguration.getVersion();
    if (version == null || version.isEmpty()) {
      throw new IllegalConfigException(
          new ConfigProblemBuilder(FATAL,
              "In order to load a Spinnaker Component's profile, you must specify a version of Spinnaker in your halconfig.")
              .build()
      );
    }

    try {
      String bomName = ProfileRegistry.bomPath(version);

      BillOfMaterials bom = strictObjectMapper.convertValue(
          yaml.load(profileRegistry.getObjectContents(bomName)),
          BillOfMaterials.class
      );

      return bom;
    } catch (RetrofitError | IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(FATAL,
              "Unable to retrieve the Spinnaker bill of materials: " + e.getMessage())
              .build()
      );
    }
  }

  public String getArtifactVersion(String deploymentName, SpinnakerArtifact artifact) {
    return getBillOfMaterials(deploymentName).getServices().getArtifactVersion(artifact);
  }

  public void writeArtifactConfig(SpinnakerArtifact artifact, String profileName, String contents) {
    if (writeableProfileRegistry == null) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "You need to set the \"spinnaker.config.input.writer\" property to \"true\" to modify base-profiles.").build());
    }
  }
}
