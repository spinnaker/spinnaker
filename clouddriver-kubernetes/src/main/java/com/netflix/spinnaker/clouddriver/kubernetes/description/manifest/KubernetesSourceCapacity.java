/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.OptionalInt;

public class KubernetesSourceCapacity {
  public static Integer getSourceCapacity(
      KubernetesManifest manifest, KubernetesCredentials credentials, OptionalInt currentVersion) {
    String name = currentManifestName(manifest, currentVersion);
    KubernetesManifest currentManifest =
        credentials.get(
            KubernetesCoordinates.builder()
                .kind(manifest.getKind())
                .namespace(manifest.getNamespace())
                .name(name)
                .build());
    if (currentManifest != null) {
      return currentManifest.getReplicas();
    }
    return null;
  }

  private static String currentManifestName(
      KubernetesManifest manifest, OptionalInt currentVersion) {
    if (currentVersion.isEmpty()) {
      return manifest.getName();
    }

    int version = currentVersion.getAsInt();
    String versionString = String.format("v%03d", version);
    return String.join("-", manifest.getName(), versionString);
  }
}
