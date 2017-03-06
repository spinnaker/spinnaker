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

import lombok.Data;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

@Data
public class BillOfMaterials {
  String version;
  Artifacts services;

  @Data
  public static class Artifacts {
    Artifact echo;
    Artifact clouddriver;
    Artifact deck;
    Artifact fiat;
    Artifact front50;
    Artifact gate;
    Artifact igor;
    Artifact orca;
    Artifact rosco;

    public String getArtifactVersion(String artifactName) {
      Optional<Field> field = Arrays.stream(Artifacts.class.getDeclaredFields())
          .filter(f -> f.getName().equals(artifactName))
          .findFirst();

      if (!field.isPresent()) {
        throw new RuntimeException("No supported spinnaker artifact named " + artifactName);
      }

      try {
        return ((Artifact) field.get().get(this)).getVersion();
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (NullPointerException e) {
        throw new RuntimeException("Spinnaker artifact " + artifactName + " is not listed in the BOM");
      }
    }

    @Data
    static class Artifact {
      String version;
      // TODO(lwander) dependencies will go here.
    }
  }
}
