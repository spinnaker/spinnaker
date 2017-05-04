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
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions.Version;
import com.netflix.spinnaker.halyard.core.registry.v1.WriteableProfileRegistry;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

@Component
public class ArtifactService {
  @Autowired(required = false)
  WriteableProfileRegistry writeableProfileRegistry;

  @Autowired
  Yaml yamlParser;

  @Autowired
  StrictObjectMapper strictObjectMapper;

  @Autowired
  DeploymentService deploymentService;

  @Autowired
  VersionsService versionsService;

  BillOfMaterials getBillOfMaterials(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    String version = deploymentConfiguration.getVersion();
    return versionsService.getBillOfMaterials(version);
  }

  public String getArtifactVersion(String deploymentName, SpinnakerArtifact artifact) {
    return getBillOfMaterials(deploymentName).getArtifactVersion(artifact.getName());
  }

  private void deleteVersion(Versions versionsCollection, String version) {
    versionsCollection.setVersions(versionsCollection.getVersions()
        .stream()
        .filter(other -> !other.getVersion().equals(version))
        .collect(Collectors.toList()));
  }

  public void deprecateVersion(Version version) {
    if (writeableProfileRegistry == null) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "You need to set the \"spinnaker.config.input.writerEnabled\" property to \"true\" to modify your halconfig bucket contents.").build());
    }

    Versions versionsCollection = versionsService.getVersions();
    deleteVersion(versionsCollection, version.getVersion());

    writeableProfileRegistry.writeVersions(yamlParser.dump(strictObjectMapper.convertValue(versionsCollection, Map.class)));
  }

  public void publishVersion(Version version) {
    if (writeableProfileRegistry == null) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "You need to set the \"spinnaker.config.input.writerEnabled\" property to \"true\" to modify your halconfig bucket contents.").build());
    }

    Versions versionsCollection = versionsService.getVersions();
    deleteVersion(versionsCollection, version.getVersion());
    versionsCollection.getVersions().add(version);

    writeableProfileRegistry.writeVersions(yamlParser.dump(strictObjectMapper.convertValue(versionsCollection, Map.class)));
  }

  public void publishLatest(String latest) {
    if (writeableProfileRegistry == null) {
      throw new HalException(new ConfigProblemBuilder(FATAL,
          "You need to set the \"spinnaker.config.input.writerEnabled\" property to \"true\" to modify BOM contents.").build());
    }

    Versions versionsCollection = versionsService.getVersions();
    boolean hasLatest = versionsCollection.getVersions().stream().anyMatch(v -> v.getVersion().equals(latest));
    if (!hasLatest) {
      throw new HalException(FATAL, "Version " + latest + " does not exist in the list of published versions");
    }

    versionsCollection.setLatest(latest);

    writeableProfileRegistry.writeVersions(yamlParser.dump(strictObjectMapper.convertValue(versionsCollection, Map.class)));
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
          yamlParser.load(bomContents),
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

    try {
      bom = strictObjectMapper.convertValue(
          yamlParser.load(IOUtils.toString(new FileInputStream(bomPath))),
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

    writeableProfileRegistry.writeArtifactConfig(bom, artifactName, profileFile.getName(), profileContents);
  }
}
