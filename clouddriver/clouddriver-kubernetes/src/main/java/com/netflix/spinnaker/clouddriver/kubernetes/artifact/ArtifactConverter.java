/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.OptionalInt;

@NonnullByDefault
public final class ArtifactConverter {
  // Static methods only; prevent instantiation.
  private ArtifactConverter() {}

  public static Artifact toArtifact(
      KubernetesManifest manifest, String account, OptionalInt version) {
    String name = manifest.getName();
    String versionString = version.isPresent() ? String.format("v%03d", version.getAsInt()) : "";
    String versionedName = versionString.isEmpty() ? name : String.join("-", name, versionString);
    return Artifact.builder()
        .type("kubernetes/" + manifest.getKind().toString())
        .name(name)
        .location(manifest.getNamespace())
        .version(versionString)
        .reference(versionedName)
        .putMetadata("account", account)
        .build();
  }
}
