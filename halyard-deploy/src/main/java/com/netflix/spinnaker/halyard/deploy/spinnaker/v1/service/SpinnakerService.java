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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.CustomProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ProfileFactory;
import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Data
@Component
abstract public class SpinnakerService<T> implements HasServiceSettings<T> {
  @Autowired
  String spinnakerStagingPath;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  ArtifactService artifactService;

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

  abstract public Type getType();
  abstract public Class<T> getEndpointClass();
  abstract public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints);

  abstract protected Optional<String> customProfileOutputPath(String profileName);

  public Optional<Profile> customProfile(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings runtimeSettings, Path profilePath, String profileName) {
    return customProfileOutputPath(profileName).flatMap(outputPath -> {
      SpinnakerArtifact artifact = getArtifact();
      ProfileFactory factory = new CustomProfileFactory() {
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

      return Optional.of(factory.getProfile(profileName, outputPath, deploymentConfiguration, runtimeSettings));
    });
  }

  public enum Type {
    CLOUDDRIVER("spin-clouddriver", "clouddriver"),
    CLOUDDRIVER_BOOTSTRAP("spin-clouddriver-bootstrap", "clouddriver-bootstrap"),
    CONSUL_CLIENT("spin-consul-client", "consul-client"),
    CONSUL_SERVER("spin-consul-server", "consul-server"),
    DECK("spin-deck", "deck"),
    ECHO("spin-echo", "echo"),
    FIAT("spin-fiat", "fiat"),
    FRONT50("spin-front50", "front50"),
    GATE("spin-gate", "gate"),
    IGOR("spin-igor", "igor"),
    ORCA("spin-orca", "orca"),
    ORCA_BOOTSTRAP("spin-orca-bootstrap", "orca-bootstrap"),
    REDIS("spin-redis", "redis"),
    REDIS_BOOTSTRAP("spin-redis-bootstrap", "redis-bootstrap"),
    ROSCO("spin-rosco", "rosco"),
    MONITORING_DAEMON("spin-monitoring-daemon", "monitoring-daemon"),
    VAULT_CLIENT("spin-vault-client", "vault-client"),
    VAULT_SERVER("spin-vault-server", "vault-server");

    @Getter
    final String serviceName;
    @Getter
    final String canonicalName;

    Type(String serviceName, String canonicalName) {
      this.serviceName = serviceName;
      this.canonicalName = canonicalName;
    }

    @Override
    public String toString() {
      return serviceName;
    }

    private static String reduceName(String name) {
      return name.replace("-", "").replace("_", "");
    }

    public static Type fromCanonicalName(String canonicalName) {
      String finalName = reduceName(canonicalName);

      return Arrays.stream(values())
          .filter(t -> reduceName(t.getCanonicalName()).equalsIgnoreCase(finalName))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("No service with canonical name " + canonicalName + " exists."));
    }
  }
}
