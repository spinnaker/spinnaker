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

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSourcesConfig;
import com.netflix.spinnaker.halyard.config.config.v1.RelaxedObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.registry.v1.GoogleWriteableProfileRegistry;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions.Version;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class ArtifactService {
  @Autowired(required = false)
  GoogleWriteableProfileRegistry googleWriteableProfileRegistry;

  @Autowired Yaml yamlParser;

  @Autowired RelaxedObjectMapper relaxedObjectMapper;

  @Autowired DeploymentService deploymentService;

  @Autowired VersionsService versionsService;

  @Autowired ArtifactSourcesConfig artifactSourcesConfig;

  BillOfMaterials getBillOfMaterials(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    String version = deploymentConfiguration.getVersion();
    return versionsService.getBillOfMaterials(version);
  }

  /**
   * Should use {@link #getArtifactSources(String, SpinnakerArtifact)} when it supports all types of
   * deployments.
   *
   * <p>To future devs: In order to remove this method for good, the remaining callers of this
   * method need to be able to incorporate different repository sources. As of this writing (May
   * 2018) the {@link
   * com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.debian.LocalDebianServiceProvider}
   * and the {@link
   * com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.debian.BakeDebianServiceProvider}
   * are the two hold outs.
   */
  @Deprecated
  public BillOfMaterials.ArtifactSources getArtifactSources(String deploymentName) {
    BillOfMaterials bom = getBillOfMaterials(deploymentName);
    return bom.getArtifactSources();
  }

  public BillOfMaterials.ArtifactSources getArtifactSources(
      String deploymentName, SpinnakerArtifact artifact) {
    BillOfMaterials bom = getBillOfMaterials(deploymentName);
    BillOfMaterials.ArtifactSources baseline = bom.getArtifactSources();
    BillOfMaterials.ArtifactSources overrides = bom.getArtifactSources(artifact.getName());
    return mergeArtifactSources(baseline, overrides);
  }

  private BillOfMaterials.ArtifactSources mergeArtifactSources(
      BillOfMaterials.ArtifactSources baseline, BillOfMaterials.ArtifactSources overrides) {
    if (baseline == null) {
      return overrides;
    }

    if (overrides == null) {
      return baseline;
    }

    BillOfMaterials.ArtifactSources merged =
        new BillOfMaterials.ArtifactSources()
            .setDebianRepository(baseline.getDebianRepository())
            .setDockerRegistry(baseline.getDockerRegistry())
            .setGitPrefix(baseline.getGitPrefix())
            .setGoogleImageProject(baseline.getGoogleImageProject());

    if (StringUtils.isNotEmpty(overrides.getDebianRepository())) {
      merged.setDebianRepository(overrides.getDebianRepository());
    }
    if (StringUtils.isNotEmpty(overrides.getDockerRegistry())) {
      merged.setDockerRegistry(overrides.getDockerRegistry());
    }
    if (StringUtils.isNotEmpty(overrides.getGitPrefix())) {
      merged.setGitPrefix(overrides.getGitPrefix());
    }
    if (StringUtils.isNotEmpty(overrides.getGoogleImageProject())) {
      merged.setGoogleImageProject(overrides.getGoogleImageProject());
    }

    return merged;
  }

  public String getArtifactVersion(String deploymentName, SpinnakerArtifact artifact) {
    return getBillOfMaterials(deploymentName).getArtifactVersion(artifact.getName());
  }

  public String getArtifactCommit(String deploymentName, SpinnakerArtifact artifact) {
    return getBillOfMaterials(deploymentName).getArtifactCommit(artifact.getName());
  }

  private void deleteVersion(Versions versionsCollection, String version) {
    versionsCollection.setVersions(
        versionsCollection.getVersions().stream()
            .filter(other -> !other.getVersion().equals(version))
            .collect(Collectors.toList()));
  }

  private static final String BAD_CONFIG_FORMAT =
      "You need to set '%s: true' in /opt/spinnaker/config/halyard-local.yml to perform this admin feature";
  private static final String NO_WRITER_ENABLED =
      String.format(BAD_CONFIG_FORMAT, "spinnaker.config.input.writerEnabled");
  private static final String NO_GCS_ENABLED =
      String.format(BAD_CONFIG_FORMAT, "spinnaker.config.input.gcs.enabled");

  public void deprecateVersion(Version version, String illegalReason) {
    if (googleWriteableProfileRegistry == null) {
      throw new HalException(FATAL, NO_WRITER_ENABLED);
    }

    Versions versionsCollection = versionsService.getVersions();
    if (versionsCollection == null) {
      throw new HalException(FATAL, NO_GCS_ENABLED);
    }

    deleteVersion(versionsCollection, version.getVersion());

    if (!StringUtils.isEmpty(illegalReason)) {
      List<Versions.IllegalVersion> illegalVersions = versionsCollection.getIllegalVersions();
      if (illegalVersions == null) {
        illegalVersions = new ArrayList<>();
      }

      illegalVersions.add(
          new Versions.IllegalVersion().setVersion(version.getVersion()).setReason(illegalReason));
      versionsCollection.setIllegalVersions(illegalVersions);
    }

    googleWriteableProfileRegistry.writeVersions(
        yamlParser.dump(relaxedObjectMapper.convertValue(versionsCollection, Map.class)));
  }

  public void publishVersion(Version version) {
    if (googleWriteableProfileRegistry == null) {
      throw new HalException(FATAL, NO_WRITER_ENABLED);
    }

    Versions versionsCollection = versionsService.getVersions();
    if (versionsCollection == null) {
      throw new HalException(FATAL, NO_GCS_ENABLED);
    }
    deleteVersion(versionsCollection, version.getVersion());
    versionsCollection.getVersions().add(version);

    googleWriteableProfileRegistry.writeVersions(
        yamlParser.dump(relaxedObjectMapper.convertValue(versionsCollection, Map.class)));
  }

  public void publishLatestSpinnaker(String latestSpinnaker) {
    if (googleWriteableProfileRegistry == null) {
      throw new HalException(FATAL, NO_WRITER_ENABLED);
    }

    Versions versionsCollection = versionsService.getVersions();
    if (versionsCollection == null) {
      throw new HalException(FATAL, NO_GCS_ENABLED);
    }
    boolean hasLatest =
        versionsCollection.getVersions().stream()
            .anyMatch(v -> v.getVersion().equals(latestSpinnaker));
    if (!hasLatest) {
      throw new HalException(
          FATAL,
          "Version " + latestSpinnaker + " does not exist in the list of published versions");
    }

    versionsCollection.setLatestSpinnaker(latestSpinnaker);

    googleWriteableProfileRegistry.writeVersions(
        yamlParser.dump(relaxedObjectMapper.convertValue(versionsCollection, Map.class)));
  }

  public void publishLatestHalyard(String latestHalyard) {
    if (googleWriteableProfileRegistry == null) {
      throw new HalException(FATAL, NO_WRITER_ENABLED);
    }

    Versions versionsCollection = versionsService.getVersions();
    if (versionsCollection == null) {
      throw new HalException(FATAL, NO_GCS_ENABLED);
    }

    versionsCollection.setLatestHalyard(latestHalyard);

    googleWriteableProfileRegistry.writeVersions(
        yamlParser.dump(relaxedObjectMapper.convertValue(versionsCollection, Map.class)));
  }

  public void writeBom(String bomPath) {
    if (googleWriteableProfileRegistry == null) {
      throw new HalException(FATAL, NO_WRITER_ENABLED);
    }

    BillOfMaterials bom;
    String bomContents;
    String version;

    try {
      bomContents = IOUtils.toString(new FileInputStream(bomPath));
      bom = relaxedObjectMapper.convertValue(yamlParser.load(bomContents), BillOfMaterials.class);
      version = bom.getVersion();
    } catch (IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(FATAL, "Unable to load Bill of Materials: " + e.getMessage())
              .build());
    }

    if (version == null) {
      throw new HalException(
          new ConfigProblemBuilder(FATAL, "No version was supplied in this BOM.").build());
    }

    googleWriteableProfileRegistry.writeBom(bom.getVersion(), bomContents);
  }

  public void writeArtifactConfig(String bomPath, String artifactName, String profilePath) {
    if (googleWriteableProfileRegistry == null) {
      throw new HalException(FATAL, NO_WRITER_ENABLED);
    }

    BillOfMaterials bom;
    File profileFile = Paths.get(profilePath).toFile();
    String profileContents;

    try {
      bom =
          relaxedObjectMapper.convertValue(
              yamlParser.load(IOUtils.toString(new FileInputStream(bomPath))),
              BillOfMaterials.class);
    } catch (IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(FATAL, "Unable to load Bill of Materials: " + e.getMessage())
              .build());
    }

    try {
      profileContents = IOUtils.toString(new FileInputStream(profileFile));
    } catch (IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(FATAL, "Unable to load profile : " + e.getMessage()).build());
    }

    googleWriteableProfileRegistry.writeArtifactConfig(
        bom, artifactName, profileFile.getName(), profileContents);
  }
}
