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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.CustomProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ProfileFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Data
@Component
@Slf4j
public abstract class SpinnakerService<T> implements HasServiceSettings<T> {
  @Autowired ObjectMapper objectMapper;

  @Autowired ArtifactService artifactService;

  @Autowired Yaml yamlParser;

  @Autowired HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Override
  public SpinnakerService<T> getService() {
    return this;
  }

  @Override
  public String getServiceName() {
    return getType().getServiceName();
  }

  public String getCanonicalName() {
    return getType().getCanonicalName();
  }

  public String getBaseCanonicalName() {
    return getType().getBaseType().getCanonicalName();
  }

  public String getSpinnakerStagingPath(String deploymentName) {
    return halconfigDirectoryStructure.getStagingPath(deploymentName).toString();
  }

  public String getSpinnakerStagingDependenciesPath(String deploymentName) {
    return halconfigDirectoryStructure.getStagingDependenciesPath(deploymentName).toString();
  }

  public ServiceSettings getDefaultServiceSettings(
      DeploymentConfiguration deploymentConfiguration) {
    File userSettingsFile =
        new File(
            halconfigDirectoryStructure
                .getUserServiceSettingsPath(deploymentConfiguration.getName())
                .toString(),
            getCanonicalName() + ".yml");

    if (userSettingsFile.exists() && userSettingsFile.length() != 0) {
      try {
        log.info("Reading user provided service settings from " + userSettingsFile);
        return objectMapper.convertValue(
            yamlParser.load(new FileInputStream(userSettingsFile)), ServiceSettings.class);
      } catch (FileNotFoundException e) {
        throw new HalException(
            Problem.Severity.FATAL, "Unable to read provided user settings: " + e.getMessage(), e);
      }
    } else {
      return new ServiceSettings();
    }
  }

  public boolean isInBillOfMaterials(DeploymentConfiguration deployment) {
    String version = getArtifactService().getArtifactVersion(deployment.getName(), getArtifact());
    return (version != null);
  }

  public abstract Type getType();

  public abstract Class<T> getEndpointClass();

  public abstract List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints);

  protected abstract Optional<String> customProfileOutputPath(String profileName);

  public Optional<Profile> customProfile(
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings runtimeSettings,
      Path profilePath,
      String profileName) {
    return customProfileOutputPath(profileName)
        .flatMap(
            outputPath -> {
              SpinnakerArtifact artifact = getArtifact();
              ProfileFactory factory =
                  new CustomProfileFactory() {
                    @Override
                    public SpinnakerArtifact getArtifact() {
                      return artifact;
                    }

                    protected ArtifactService getArtifactService() {
                      return artifactService;
                    }

                    @Override
                    protected Path getUserProfilePath() {
                      return profilePath;
                    }
                  };

              return Optional.of(
                  factory.getProfile(
                      profileName, outputPath, deploymentConfiguration, runtimeSettings));
            });
  }

  public boolean hasTypeModifier() {
    return getType().getModifier() != null;
  }

  public String getTypeModifier() {
    return getType().getModifier();
  }

  public enum Type {
    CLOUDDRIVER("clouddriver"),
    CLOUDDRIVER_BOOTSTRAP(CLOUDDRIVER, "bootstrap"),
    CLOUDDRIVER_CACHING(CLOUDDRIVER, "caching"),
    CLOUDDRIVER_RO(CLOUDDRIVER, "ro"),
    CLOUDDRIVER_RO_DECK(CLOUDDRIVER, "ro-deck"),
    CLOUDDRIVER_RW(CLOUDDRIVER, "rw"),
    CONSUL_CLIENT("consul-client"),
    CONSUL_SERVER("consul-server"),
    DECK("deck"),
    ECHO("echo"),
    ECHO_SCHEDULER(ECHO, "scheduler"),
    ECHO_WORKER(ECHO, "worker"),
    FIAT("fiat"),
    FRONT50("front50"),
    GATE("gate"),
    IGOR("igor"),
    KAYENTA("kayenta"),
    ORCA("orca"),
    ORCA_BOOTSTRAP(ORCA, "bootstrap"),
    REDIS("redis"),
    REDIS_BOOTSTRAP(REDIS, "bootstrap"),
    ROSCO("rosco"),
    MONITORING_DAEMON("monitoring-daemon"),
    VAULT_CLIENT("vault-client"),
    VAULT_SERVER("vault-server");

    @Getter final String canonicalName;
    @Getter final String serviceName;
    @Getter final String modifier;
    @Getter final Type baseType;

    Type(String canonicalName) {
      this.canonicalName = canonicalName;
      this.serviceName = "spin-" + canonicalName;
      this.modifier = null;
      this.baseType = this;
    }

    Type(Type baseType, String modifier) {
      this.canonicalName = baseType.getCanonicalName() + "-" + modifier;
      this.serviceName = "spin-" + this.canonicalName;
      this.baseType = baseType;
      this.modifier = modifier;
    }

    @JsonValue
    public String asYamlKey() {
      // When SpinnakerRuntimeSettings is serialized, we expect its keys to be camel-cased.
      return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, canonicalName);
    }

    private static String reduceName(String name) {
      return name.replace("-", "").replace("_", "");
    }

    public static Type fromCanonicalName(String canonicalName) {
      String finalName = reduceName(canonicalName);

      return Arrays.stream(values())
          .filter(t -> reduceName(t.getCanonicalName()).equalsIgnoreCase(finalName))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "No service with canonical name " + canonicalName + " exists."));
    }
  }
}
