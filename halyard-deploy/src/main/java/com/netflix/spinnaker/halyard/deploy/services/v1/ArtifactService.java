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

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.halyard.config.config.v1.StrictObjectMapper;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.registry.ProfileRegistry;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.registry.WriteableProfileRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import retrofit.RetrofitError;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;

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

  public void writeBom(String bomPath) {
    if (writeableProfileRegistry == null) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "You need to set the \"spinnaker.config.input.writerEnabled\" property to \"true\" to modify BOM contents.").build());
    }

    BillOfMaterials bom;
    String bomContents;
    String version;

    try {
      bomContents = IOUtils.toString(new FileInputStream(bomPath));
      bom = strictObjectMapper.convertValue(
          yaml.load(bomContents),
          BillOfMaterials.class);
      version = bom.getVersion();
    } catch (IOException e) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "Unable to load Bill of Materials: " + e.getMessage()).build()
      );
    }

    if (version == null) {
      throw new HalException(new ConfigProblemBuilder(FATAL, "No version was supplied in this BOM.").build());
    }

    writeableProfileRegistry.writeBom(bom.getVersion(), bomContents);
  }

  public void writeArtifactConfig(String bomPath, String artifactName, String profilePath) {
    if (writeableProfileRegistry == null) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "You need to set the \"spinnaker.config.input.writerEnabled\" property to \"true\" to modify base-profiles.").build());
    }

    BillOfMaterials bom;
    File profileFile = Paths.get(profilePath).toFile();
    String profileContents;
    SpinnakerArtifact spinnakerArtifact;

    try {
      bom = strictObjectMapper.convertValue(
          yaml.load(IOUtils.toString(new FileInputStream(bomPath))),
          BillOfMaterials.class);
    } catch (IOException e) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "Unable to load Bill of Materials: " + e.getMessage()).build()
      );
    }

    try {
       profileContents = IOUtils.toString(new FileInputStream(profileFile));
    } catch (IOException e) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "Unable to load profile : " + e.getMessage()).build()
      );
    }

    try {
      spinnakerArtifact = SpinnakerArtifact.fromString(artifactName);
    } catch (RuntimeException e) {
      throw new HalException(new ConfigProblemBuilder(FATAL, e.getMessage()).build());
    }

    writeableProfileRegistry.writeArtifactConfig(bom, spinnakerArtifact, profileFile.getName(), profileContents);
  }
}
