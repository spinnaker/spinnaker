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

package com.netflix.spinnaker.halyard.core.registry.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import lombok.Data;

@Data
public class BillOfMaterials {
  String version;
  String timestamp;
  String hostname;
  Services services;
  Dependencies dependencies;
  ArtifactSources artifactSources;

  @Data
  static class Dependencies {
    Artifact redis;
    Artifact consul;
    Artifact vault;

    Artifact defaultArtifact;

    String getArtifactVersion(String artifactName) {
      return getFieldArtifact(Dependencies.class, this, artifactName).getVersion();
    }

    String getArtifactCommit(String artifactName) {
      return getFieldArtifact(Dependencies.class, this, artifactName).getCommit();
    }

    ArtifactSources getArtifactSources(String artifactName) {
      return getFieldArtifact(Dependencies.class, this, artifactName).getArtifactSources();
    }
  }

  @Data
  public static class ArtifactSources {
    String debianRepository;
    String dockerRegistry;
    String googleImageProject;
    String gitPrefix;
  }

  @Data
  static class Services {
    Artifact echo;
    Artifact clouddriver;
    Artifact deck;
    Artifact fiat;
    Artifact front50;
    Artifact gate;
    Artifact igor;
    Artifact kayenta;
    Artifact orca;
    Artifact rosco;

    @JsonProperty("monitoring-third-party")
    Artifact monitoringThirdParty;

    @JsonProperty("monitoring-daemon")
    Artifact monitoringDaemon;

    Artifact spinnaker;

    Artifact defaultArtifact = new Artifact();

    String getArtifactVersion(String artifactName) {
      return getFieldArtifact(Services.class, this, artifactName).getVersion();
    }

    String getArtifactCommit(String artifactName) {
      return getFieldArtifact(Services.class, this, artifactName).getCommit();
    }

    ArtifactSources getArtifactSources(String artifactName) {
      return getFieldArtifact(Services.class, this, artifactName).getArtifactSources();
    }
  }

  @Data
  static class Artifact {
    String version;
    String commit;
    ArtifactSources artifactSources;
  }

  private static <T> Artifact getFieldArtifact(Class<T> clazz, T obj, String artifactName) {
    Optional<Field> field =
        Arrays.stream(clazz.getDeclaredFields())
            .filter(
                f -> {
                  boolean nameMatches = f.getName().equals(artifactName);
                  boolean propertyMatches = false;
                  JsonProperty property = f.getDeclaredAnnotation(JsonProperty.class);
                  if (property != null) {
                    propertyMatches = property.value().equals(artifactName);
                  }
                  return nameMatches || propertyMatches;
                })
            .findFirst();

    try {
      Artifact result =
          (Artifact) field.orElseThrow(() -> new NoKnownArtifact(artifactName)).get(obj);

      if (result == null && !artifactName.equals("defaultArtifact")) {
        result = getFieldArtifact(clazz, obj, "defaultArtifact");
      }

      if (result == null) {
        result = new Artifact();
      }

      return result;
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (NullPointerException e) {
      throw new RuntimeException(
          "Versioned artifact " + artifactName + " is not listed in the BOM");
    }
  }

  static class NoKnownArtifact extends RuntimeException {
    NoKnownArtifact(String msg) {
      super(msg);
    }
  }

  public String getArtifactVersion(String artifactName) {
    try {
      return services.getArtifactVersion(artifactName);
    } catch (NoKnownArtifact ignored) {
    }

    try {
      return dependencies.getArtifactVersion(artifactName);
    } catch (NoKnownArtifact ignored) {
    }

    throw new IllegalArgumentException(
        "No artifact with name " + artifactName + " could be found in the BOM");
  }

  public String getArtifactCommit(String artifactName) {
    try {
      return services.getArtifactCommit(artifactName);
    } catch (NoKnownArtifact ignored) {
    }

    try {
      return dependencies.getArtifactCommit(artifactName);
    } catch (NoKnownArtifact ignored) {
    }

    throw new IllegalArgumentException(
        "No artifact with name " + artifactName + " could be found in the BOM");
  }

  public ArtifactSources getArtifactSources(String artifactName) {
    try {
      return services.getArtifactSources(artifactName);
    } catch (NoKnownArtifact ignored) {
    }

    try {
      return dependencies.getArtifactSources(artifactName);
    } catch (NoKnownArtifact ignored) {
    }

    return null;
  }
}
